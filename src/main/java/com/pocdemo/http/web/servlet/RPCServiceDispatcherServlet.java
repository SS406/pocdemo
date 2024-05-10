package com.pocdemo.http.web.servlet;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tomcat.util.http.fileupload.IOUtils;

import com.pocdemo.http.common.lambda.Lambdas;
import com.pocdemo.http.common.lambda.Lambdas.UncheckedBiFunction;
import com.pocdemo.http.config.JSONUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

public class RPCServiceDispatcherServlet extends HttpServlet {

	private static final Logger LOGGER = System.getLogger(RPCServiceDispatcherServlet.class.getName());

	static record Service(BiConsumer<HttpServletRequest, HttpServletResponse> function) {

	}

	static record ActionKey(String path, String httpMathod) {

	}

	final Map<ActionKey, Service> mappings = new HashMap<>();

	static final UncheckedBiFunction<String, HttpServletRequest, Part> partExtractor = (n, r) -> r.getPart(n);

	static final Function<Part, String> partToString = p -> null != p
			? Lambdas.closing(p::getInputStream, is -> new String(is.readAllBytes()))
			: null;

	public RPCServiceDispatcherServlet(Set<Object> services) {
		@SuppressWarnings("unchecked")
		Class<? extends Annotation>[] webAnnotations = new Class[] { GET.class, POST.class, PUT.class, DELETE.class };
		Predicate<Method> isWebMethod = method -> Stream.of(webAnnotations)
				.map(method::isAnnotationPresent)
				.findAny().isPresent();
		Function<Method, List<String>> httpMethodGetter = method -> Stream.of(webAnnotations)
				.filter(method::isAnnotationPresent)
				.map(Class::getSimpleName)
				.collect(Collectors.toList());

		for (Object instance : services) {
			Class<?> type = instance.getClass();
			String domainPath = type.getAnnotation(Path.class).value();
			Stream.of(type.getMethods()).filter(isWebMethod).forEach(mthd -> {
				String actionPath = domainPath
						+ (null != mthd.getAnnotation(Path.class)
								? mthd.getAnnotation(Path.class).value()
								: "");
				int paramCount = mthd.getParameters().length;
				List<Function<HttpServletRequest, Object>> readers = new ArrayList<>(paramCount);
				for (var prm : mthd.getParameters()) {
					readers.add(createReader(prm));
				}
				var action = createInvoker(instance, mthd, readers);
				httpMethodGetter.apply(mthd)
						.forEach(webMethod -> mappings.put(
								new ActionKey(actionPath, webMethod),
								new Service(action)));
			});
		}
	}

	static BiConsumer<HttpServletRequest, HttpServletResponse> createParameterReaderFn(Method mthd, Object[] params,
			List<Function<HttpServletRequest, Object>> readers) {
		return (rq, rs) -> {
			try {
				for (int i = 0; i < params.length; i++) {
					var reader = readers.get(i);
					if (null == reader) {
						var typ = mthd.getParameters()[i].getType();
						if (HttpServletRequest.class.isAssignableFrom(typ)) {
							params[i] = rq;
						} else if (HttpServletResponse.class.isAssignableFrom(typ)) {
							params[i] = rs;
						} else if (OutputStream.class.isAssignableFrom(typ)) {
							params[i] = rs.getOutputStream();
						} else if (InputStream.class.isAssignableFrom(typ)) {
							params[i] = rq.getInputStream();
						}
					} else {
						params[i] = readers.get(i).apply(rq);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				LOGGER.log(Level.ERROR, "failed to parse method parameters", e);
				throw new WebException(HttpServletResponse.SC_BAD_REQUEST, "Bad request");
			}
		};
	}

	static BiConsumer<HttpServletRequest, HttpServletResponse> createMethodInvokerFn(Object service, Method mthd,
			Object[] params) {
		var ann = mthd.getAnnotation(Produces.class);
		return (rq, rs) -> {
			Object ret = null;
			try {
				ret = mthd.invoke(service, params);
				if (null != ret) {
					if (File.class.isAssignableFrom(mthd.getReturnType())) {
						var file = File.class.cast(ret);
						rs.setContentType(MediaType.APPLICATION_OCTET_STREAM);
						rs.setHeader(HttpHeaders.CONTENT_DISPOSITION,
								"attachment; filename=\"" + file.getName() + "\"");
						try (var os = rs.getOutputStream(); var is = Files.newInputStream(file.toPath())) {
							IOUtils.copyLarge(is, os);
						}
					} else if (ret instanceof String && null != ann && MediaType.TEXT_PLAIN.equals(ann.value()[0])) {
						rs.setContentType(MediaType.TEXT_PLAIN);
						var value = ret;
						Lambdas.takeAndClose(rs::getWriter, w -> w.println(value));
					} else {
						var jsn = JSONUtil.toJson(ret);
						rs.setContentType(MediaType.APPLICATION_JSON);
						Lambdas.takeAndClose(rs::getWriter, w -> w.write(jsn));
					}
				}
			} catch (InvocationTargetException ie) {
				var ex = ie.getCause();
				if (ex instanceof RuntimeException) {
					throw (RuntimeException) ex;
				} else {
					LOGGER.log(Level.ERROR, "Internal excep", ex);
					throw new WebException("Unexpected failure");
				}
			} catch (Exception e) {
				LOGGER.log(Level.ERROR, "Internal excep", e);
				throw new WebException("Unexpected error");
			}
		};
	}

	BiConsumer<HttpServletRequest, HttpServletResponse> createInvoker(Object service, Method mthd,
			List<Function<HttpServletRequest, Object>> readers) {
		Object[] params = new Object[mthd.getParameters().length];
		BiConsumer<HttpServletRequest, HttpServletResponse> paramReader = createParameterReaderFn(mthd, params,
				readers);

		BiConsumer<HttpServletRequest, HttpServletResponse> invoker = createMethodInvokerFn(service, mthd, params);
		return paramReader.andThen(invoker);
	}

	private Function<HttpServletRequest, Object> createReader(Parameter prm) {
		Function<String, Object> caster = v -> cast(v, prm.getType());

		Function<HttpServletRequest, Object> fromText = r -> Lambdas.closing(r::getInputStream,
				is -> new String(is.readAllBytes()));

		Function<HttpServletRequest, Object> fromJson = r -> Lambdas.closing(r::getInputStream,
				is -> JSONUtil.fromJson(is, prm.getType()));

		Function<HttpServletRequest, Object> fromParams = r -> {
			Map<String, String> map = new HashMap<>();
			r.getParameterMap().forEach((k, v) -> map.put(k, v[0]));
			return JSONUtil.fromJson(JSONUtil.toJson(map), prm.getType());
		};

		Function<HttpServletRequest, Object> fromParts = r -> {
			Map<String, String> map = new HashMap<>();
			Lambdas.apply(r, HttpServletRequest::getParts).forEach(p -> map.put(p.getName(), partToString.apply(p)));
			return JSONUtil.fromJson(JSONUtil.toJson(map), prm.getType());
		};

		Function<HttpServletRequest, String> requestToString;
		Function<HttpServletRequest, Object> reader;

		if (prm.isAnnotationPresent(HeaderParam.class)) {
			requestToString = r -> r.getHeader(prm.getAnnotation(HeaderParam.class).value());
			reader = requestToString.andThen(caster);

		} else if (prm.isAnnotationPresent(QueryParam.class)) {
			requestToString = r -> r.getParameter(prm.getAnnotation(QueryParam.class).value());
			reader = requestToString.andThen(caster);

		} else if (prm.isAnnotationPresent(FormParam.class)) {
			// form
			String partName = prm.getAnnotation(FormParam.class).value();
			if (Part.class.isAssignableFrom(prm.getType())) {
				reader = r -> Lambdas.get(() -> r.getPart(partName));

			} else if (Boolean.TRUE.equals(isBasicType(prm.getType()))) {
				requestToString = r -> MediaType.APPLICATION_FORM_URLENCODED.equals(r.getContentType())
						? r.getParameter(partName)
						: partExtractor.andThen(partToString).apply(partName, r);
				reader = requestToString.andThen(caster);
			} else {
				// assuming json part
				requestToString = r -> Lambdas.closing(
						() -> r.getPart(partName).getInputStream(),
						is -> new String(is.readAllBytes()));
				reader = requestToString.andThen(v -> JSONUtil.fromJson(v, prm.getType()));
			}
		} else if (HttpServletRequest.class.isAssignableFrom(prm.getType())) {
			reader = r -> r;

		} else if (InputStream.class.isAssignableFrom(prm.getType())) {
			reader = r -> Lambdas.apply(r, HttpServletRequest::getInputStream);

		} else if (OutputStream.class.isAssignableFrom(prm.getType())
				|| HttpServletResponse.class.isAssignableFrom(prm.getType())) {
			reader = null;

		} else {
			reader = r -> switch (r.getContentType()) {
				case MediaType.TEXT_PLAIN -> fromText.apply(r);
				case MediaType.APPLICATION_JSON -> fromJson.apply(r);
				case MediaType.APPLICATION_FORM_URLENCODED -> fromParams.apply(r);
				case MediaType.MULTIPART_FORM_DATA -> fromParts.apply(r);
				default -> throw new WebException(400, "Unexpected content type: " + r.getContentType());
			};
		}
		return reader;
	}

	private Boolean isBasicType(Class<?> type) {
		Set<Class<?>> fieldTypes = Set.of(Integer.class, Long.class,
				Float.class, Double.class, BigDecimal.class,
				LocalDate.class, LocalDateTime.class,
				String.class);
		return fieldTypes.stream().anyMatch(c -> c.isAssignableFrom(type));
	}

	private <T> T cast(String value, Class<T> type) {
		Object rawVal;
		if (null == value) {
			rawVal = null;
		} else if (Integer.class.isAssignableFrom(type)) {
			rawVal = Integer.valueOf(value);
		} else if (Long.class.isAssignableFrom(type)) {
			rawVal = Long.valueOf(value);
		} else if (Float.class.isAssignableFrom(type)) {
			rawVal = Float.valueOf(value);
		} else if (Double.class.isAssignableFrom(type)) {
			rawVal = Double.valueOf(value);
		} else if (BigDecimal.class.isAssignableFrom(type)) {
			rawVal = new BigDecimal(value);
		} else if (LocalDate.class.isAssignableFrom(type)) {
			rawVal = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
		} else if (LocalDateTime.class.isAssignableFrom(type)) {
			rawVal = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} else if (String.class.isAssignableFrom(type)) {
			rawVal = value;
		} else {
			throw new IllegalStateException("Unrecognised Type:" + type);
		}
		return type.cast(rawVal);
	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException {
		var mappingKey = getMappingKey(request);
		Service srv = mappings.get(mappingKey);
		if (null == srv) {
			LOGGER.log(Level.INFO, "Mapping not found: {0}", mappingKey);
			response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		} else {
			try {
				srv.function().accept(request, response);
			} catch (WebException wx) {
				response.setStatus(wx.getHttpCode());
				response.setContentType(MediaType.TEXT_PLAIN);
				Lambdas.takeAndClose(response::getWriter, w -> w.print(wx.getMessage()));
			} catch (Exception ex) {
				LOGGER.log(Level.ERROR, "Method invocation failed", ex);
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		}
	}

	private ActionKey getMappingKey(HttpServletRequest request) {
		var uri = request.getRequestURI();
		var toks = uri.split("\\/");
		var sep = "/";
		String servicePath = sep + toks[1];
		if (toks.length > 2) {
			servicePath += sep + toks[2];
		}
		return new ActionKey(servicePath, request.getMethod());
	}
}
