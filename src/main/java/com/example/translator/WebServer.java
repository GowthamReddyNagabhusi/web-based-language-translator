package com.example.translator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);

    public static void main(String[] args) throws Exception {

        Properties config = loadConfig();
        int port = Integer.parseInt(config.getProperty("server.port", "8080"));

        // Allow PORT env var override (common in Docker/Cloud deployments)
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try { port = Integer.parseInt(envPort.trim()); } catch (NumberFormatException ignored) {}
        }

        Server server = new Server(port);
        server.setStopAtShutdown(true);
        server.setStopTimeout(5000);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        handler.setWelcomeFiles(new String[]{"index.html"});

        // Static files from resources/webapp
        URL resourceUrl = WebServer.class.getClassLoader().getResource("webapp");
        if (resourceUrl == null) {
            LOG.error("Static resources not found in classpath — aborting startup");
            throw new IllegalStateException("Static resources not found in classpath");
        }
        handler.setResourceBase(resourceUrl.toExternalForm());
        handler.addServlet(DefaultServlet.class, "/");

        // API endpoints
        handler.addServlet(new ServletHolder(new TranslateServlet()), "/translate");
        handler.addServlet(new ServletHolder(new HealthServlet()), "/health");

        server.setHandler(handler);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down server...");
            try { server.stop(); } catch (Exception e) { LOG.error("Error during shutdown", e); }
        }));

        LOG.info("========================================");
        LOG.info("Translation Server starting on port {}", port);
        LOG.info("Open http://localhost:{} in your browser", port);
        LOG.info("========================================");

        server.start();
        server.join();
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
                LOG.info("Loaded config.properties");
            } else {
                LOG.warn("config.properties not found, using defaults");
            }
        } catch (Exception e) {
            LOG.warn("Failed to load config.properties: {}", e.getMessage());
        }
        return props;
    }
}