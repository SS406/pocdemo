package com.pocdemo.http.web.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.Constants;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServlet;

public class CustomTomcat extends Tomcat {
    private static final Logger LOGGER = System.getLogger(CustomTomcat.class.getName());
    private final StandardContext myContext;
    private final Path appDir;
    private final String tmpUploadDir;

    public CustomTomcat(Boolean enableAutoScanning, String contextPath) throws IOException {
        super();

        Path serverXmlPath = getResourceAsPath("server.xml");
        appDir = Files.createTempDirectory(UUID.randomUUID().toString());
        tmpUploadDir = Files.createDirectories(Paths.get(appDir.toString(), "tmpUpload")).toString();
        System.setProperty(Constants.CATALINA_HOME_PROP, appDir.toFile().getAbsolutePath());

        Catalina catalina = new Catalina();
        catalina.setConfigFile(serverXmlPath.toString());
        catalina.load();
        this.server = catalina.getServer();
        Files.delete(serverXmlPath);

        Path rootPath = Paths.get(".").toAbsolutePath();
        this.myContext = (StandardContext) this.addContext(contextPath, rootPath.toString());
        if (Boolean.TRUE.equals(enableAutoScanning)) {
            myContext.addLifecycleListener(new ContextConfig());
            final WebResourceRoot root = new StandardRoot(myContext);
            final URL url = rootPath.toUri().toURL();
            root.createWebResourceSet(WebResourceRoot.ResourceSetType.PRE, "/WEB-INF/classes", url, "/");
            myContext.setResources(root);
        }
    }

    @Override
    public void stop() {
        try {
            super.stop();
            FileUtils.deleteDirectory(appDir.toFile());
            LOGGER.log(Level.INFO, "Server stopped successfully");
        } catch (Exception e) {
            // supporess
        }
    }

    public <T extends HttpServlet> void addDispatcher(String path, T servlet) {
        Wrapper wrapper = Tomcat.addServlet(myContext, servlet.getClass().getName(), servlet);
        wrapper.addMapping(path);
        wrapper.setMultipartConfigElement(new MultipartConfigElement(tmpUploadDir));
    }

    private static Path getResourceAsPath(String name) throws IOException {
        Path tmpPath = Files.createTempFile("res-", "-xml");
        try (InputStream is = CustomTomcat.class.getClassLoader().getResourceAsStream(name);
                OutputStream os = Files.newOutputStream(tmpPath)) {
            IOUtils.copy(is, os);
        }
        return tmpPath;
    }

    public StandardContext defultContext() {
        return myContext;
    }
}
