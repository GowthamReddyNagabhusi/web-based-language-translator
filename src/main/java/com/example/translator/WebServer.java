package com.example.translator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;
import java.net.URL;

public class WebServer {

    public static void main(String[] args) throws Exception {

        Server server = new Server(8080);
        server.setStopAtShutdown(true);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        handler.setWelcomeFiles(new String[]{"index.html"});

        // ---- STATIC FILES SETUP ----
        // Load files from resources/webapp
        URL resourceUrl = WebServer.class.getClassLoader().getResource("webapp");
        if (resourceUrl == null) {
            throw new IllegalStateException("Static resources not found in classpath");
        }
        handler.setResourceBase(resourceUrl.toExternalForm());
        handler.addServlet(DefaultServlet.class, "/");

        // ---- API ENDPOINT ----
        handler.addServlet(new ServletHolder(new TranslateServlet()), "/translate");

        server.setHandler(handler);

        System.out.println("========================================");
        System.out.println("🚀 Translation Server Started!");
        System.out.println("📍 Open http://localhost:8080 in your browser");
        System.out.println("========================================");

        server.start();
        server.join();
    }
}