package com.example.translator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;

public class WebServer {

    public static void main(String[] args) throws Exception {

        Server server = new Server(8080);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");

        // ---- STATIC FILES SETUP ----
        // Load files from resources/webapp
        handler.setResourceBase(WebServer.class.getClassLoader().getResource("webapp").toExternalForm());
        handler.addServlet(DefaultServlet.class, "/");

        // ---- API ENDPOINT ----
        handler.addServlet(new ServletHolder(new TranslateServlet()), "/translate");

        server.setHandler(handler);

        System.out.println("Server running at http://localhost:8080");
        server.start();
        server.join();
    }
}
