package com.pocdemo.exec;

import java.util.Set;

import com.pocdemo.http.web.server.CustomTomcat;
import com.pocdemo.http.web.servlet.RPCServiceDispatcherServlet;

public class Main {
    public static void main(String[] args) throws Exception {
        Set<Object> services = Set.of(
                new Test());

        var ctxPath = "";
        var tomcat = new CustomTomcat(false, ctxPath);
        tomcat.addDispatcher("/*", new RPCServiceDispatcherServlet(services));

        tomcat.start();
    }
}
