package com.pocdemo.http.web.servlet;

import org.apache.tomcat.util.http.fileupload.IOUtils;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

//@WebServlet(urlPatterns = "/swagger-ui/*", loadOnStartup = 3)
public class SwaggerUIServlet extends HttpServlet {

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) {

        try (var is = getClass().getResourceAsStream("/swagger-ui" + request.getPathInfo())) {
            if (null != is && request.getPathInfo().endsWith("index.html")) {
                var page = new String(is.readAllBytes());
                page = page.replace("${host}", "" + request.getServerName());
                page = page.replace("${port}", "" + request.getServerPort());
                page = page.replace("${ctxPath}", "/openapi");
                try (var writer = response.getWriter()) {
                    writer.write(page);
                }
            } else {
                try (var os = response.getOutputStream()) {
                    IOUtils.copy(is, os);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
