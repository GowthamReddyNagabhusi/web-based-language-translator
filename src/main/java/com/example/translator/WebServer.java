package com.example.translator;

import com.example.translator.api.error.ErrorHandler;
import com.example.translator.api.filter.MetricsFilter;
import com.example.translator.config.AppConfig;
import com.example.translator.metrics.MetricsRegistry;
import com.example.translator.provider.*;
import com.example.translator.service.TranslationService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.MDC;

/**
 * Application bootstrap — creates all components and starts the embedded Jetty server.
 *
 * Architecture:
 *   WebServer (bootstrap)
 *     → TranslationService (caching + circuit breakers)
 *       → GoogleTranslateProvider | LibreTranslateProvider | MyMemoryProvider
 *     → TranslateServlet, HealthServlet, ReadinessServlet, MetricsServlet
 *     → SecurityFilter, RateLimitFilter, MetricsFilter
 */
public class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);
    private static final long START_TIME = System.currentTimeMillis();
    private static final AtomicBoolean READY = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        int port = resolvePort();

        // Build HTTP client shared by all providers
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(AppConfig.CONNECT_TIMEOUT_SECONDS))
                .build();

        // Build provider chain
        List<TranslationProvider> providers = List.of(
                new GoogleTranslateProvider(httpClient),
                new LibreTranslateProvider(httpClient),
                new MyMemoryProvider(httpClient)
        );

        // Build service
        TranslationService translationService = new TranslationService(providers);

        // Build server
        Server server = new Server(port);
        server.setStopAtShutdown(true);
        server.setStopTimeout(5000);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        handler.setContextPath("/");
        handler.setWelcomeFiles(new String[]{"index.html"});

        // Filters (applied in order)
        handler.addFilter(new FilterHolder(new SecurityFilter()), "/*",
                EnumSet.of(DispatcherType.REQUEST));
        handler.addFilter(new FilterHolder(new MetricsFilter()), "/*",
                EnumSet.of(DispatcherType.REQUEST));
        handler.addFilter(new FilterHolder(new RateLimitFilter(AppConfig.RATE_LIMIT_PER_MINUTE)),
                "/translate", EnumSet.of(DispatcherType.REQUEST));

        // Static resources
        URL resourceUrl = WebServer.class.getClassLoader().getResource("webapp");
        if (resourceUrl == null) {
            LOG.error("Static resources not found — aborting");
            throw new IllegalStateException("Static resources not found");
        }
        handler.setResourceBase(resourceUrl.toExternalForm());
        handler.addServlet(DefaultServlet.class, "/");

        // API endpoints
        handler.addServlet(new ServletHolder(new TranslateServlet(translationService)), "/translate");
        handler.addServlet(new ServletHolder(new HealthServletInternal(translationService)), "/health");
        handler.addServlet(new ServletHolder(new ReadinessServlet()), "/ready");
        handler.addServlet(new ServletHolder(new PrometheusMetricsServlet()), "/metrics");

        server.setHandler(handler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down server...");
            READY.set(false);
            try { server.stop(); } catch (Exception e) { LOG.error("Shutdown error", e); }
        }));

        LOG.info("========================================");
        LOG.info("Translation Server v2.0 starting on port {}", port);
        LOG.info("Providers: {}", providers.stream().map(TranslationProvider::name).toList());
        LOG.info("Cache: maxSize={}, ttl={}min", AppConfig.CACHE_MAX_SIZE, AppConfig.CACHE_TTL_MINUTES);
        LOG.info("Rate limit: {} req/min per IP", AppConfig.RATE_LIMIT_PER_MINUTE);
        LOG.info("========================================");

        server.start();
        READY.set(true);
        LOG.info("Server READY at http://localhost:{}", port);
        server.join();
    }

    private static int resolvePort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try { return Integer.parseInt(envPort.trim()); } catch (NumberFormatException ignored) {}
        }
        return AppConfig.SERVER_PORT;
    }

    // ======== Inner Servlet: Translate ========

    static class TranslateServlet extends HttpServlet {
        private static final Logger LOG = LoggerFactory.getLogger(TranslateServlet.class);
        private static final Gson GSON = new Gson();
        private static final int MAX_BODY_BYTES = AppConfig.MAX_BODY_BYTES;
        private final TranslationService service;
        private String[] allowedOrigins;

        TranslateServlet(TranslationService service) {
            this.service = service;
        }

        @Override
        public void init() {
            allowedOrigins = AppConfig.CORS_ALLOWED_ORIGINS.clone();
            LOG.info("CORS allowed origins: {}", String.join(", ", allowedOrigins));
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String requestId = resolveRequestId(req);
            MDC.put("requestId", requestId);
            resp.setHeader("X-Request-Id", requestId);
            try {
                handleTranslate(req, resp, requestId);
            } finally {
                MDC.remove("requestId");
            }
        }

        @Override
        protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
            setCorsHeaders(req, resp);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }

        private void handleTranslate(HttpServletRequest req, HttpServletResponse resp, String requestId) throws IOException {
            setCorsHeaders(req, resp);
            req.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.setContentType("application/json; charset=UTF-8");

            String text = null, lang = null;
            String contentType = req.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("application/json")) {
                JsonObject obj = readJsonBody(req);
                if (obj != null) {
                    if (obj.has("text") && !obj.get("text").isJsonNull()) text = obj.get("text").getAsString();
                    if (obj.has("lang") && !obj.get("lang").isJsonNull()) lang = obj.get("lang").getAsString();
                }
            }
            if (text == null) text = req.getParameter("text");
            if (lang == null) lang = req.getParameter("lang");

            LOG.info("Translate request: lang={}, textLength={}", lang, text != null ? text.length() : 0);

            // Validation
            if (text == null || text.isBlank()) {
                ErrorHandler.sendError(resp, 400, "Please provide text to translate", requestId);
                return;
            }
            if (text.length() > AppConfig.MAX_TEXT_LENGTH) {
                ErrorHandler.sendError(resp, 400, "Text exceeds maximum length of " + AppConfig.MAX_TEXT_LENGTH, requestId);
                return;
            }
            if (lang == null || lang.isBlank()) {
                ErrorHandler.sendError(resp, 400, "Please select a language", requestId);
                return;
            }
            if (!TranslationService.isValidLanguage(lang.trim())) {
                ErrorHandler.sendError(resp, 400, "Unsupported language code: " + lang, requestId);
                return;
            }

            try {
                TranslationResult result = service.translate(text, lang.trim());
                JsonObject out = new JsonObject();
                out.addProperty("translatedText", result.translatedText());
                if (result.pronunciation() != null && !result.pronunciation().isBlank()) {
                    out.addProperty("pronunciation", result.pronunciation());
                }
                out.addProperty("provider", result.provider());
                out.addProperty("detectedSourceLang", "auto");
                resp.getWriter().write(GSON.toJson(out));

                LOG.info("Translation OK: provider={}, lang={}, inputLen={}, outputLen={}",
                        result.provider(), lang, text.length(), result.translatedText().length());
            } catch (TranslationException e) {
                LOG.error("Translation failed: {}", e.getMessage());
                ErrorHandler.sendError(resp, 503, "Translation service temporarily unavailable", requestId);
            }
        }

        private void setCorsHeaders(HttpServletRequest req, HttpServletResponse resp) {
            String origin = req.getHeader("Origin");
            String allowed = resolveAllowedOrigin(origin);
            if (allowed != null) {
                resp.setHeader("Access-Control-Allow-Origin", allowed);
                if (!"*".equals(allowed)) resp.setHeader("Vary", "Origin");
            }
            resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept, X-Request-Id");
        }

        private String resolveAllowedOrigin(String origin) {
            for (String a : allowedOrigins) {
                if ("*".equals(a)) return "*";
                if (a.equalsIgnoreCase(origin)) return origin;
            }
            return null;
        }

        private JsonObject readJsonBody(HttpServletRequest req) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                char[] buf = new char[4096];
                int totalRead = 0, n;
                while ((n = reader.read(buf)) != -1) {
                    totalRead += n;
                    if (totalRead > MAX_BODY_BYTES) {
                        LOG.warn("Request body exceeds {} bytes", MAX_BODY_BYTES);
                        return null;
                    }
                    sb.append(buf, 0, n);
                }
                return GSON.fromJson(sb.toString(), JsonObject.class);
            } catch (Exception e) {
                LOG.warn("Failed to parse JSON body: {}", e.getMessage());
                return null;
            }
        }

        private static String resolveRequestId(HttpServletRequest req) {
            String id = req.getHeader("X-Request-Id");
            if (id != null && !id.isBlank() && id.length() <= 64) {
                // Sanitize: only allow alphanumeric, dashes and underscores
                if (id.matches("[a-zA-Z0-9\\-_]+")) return id;
            }
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    // ======== Inner Servlet: Health ========

    static class HealthServletInternal extends HttpServlet {
        private static final Gson GSON = new Gson();
        private final TranslationService service;

        HealthServletInternal(TranslationService service) {
            this.service = service;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json; charset=UTF-8");
            resp.setStatus(200);

            long uptimeSec = (System.currentTimeMillis() - START_TIME) / 1000;
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            var cacheStats = service.cacheStats();

            JsonObject json = new JsonObject();
            json.addProperty("status", "UP");
            json.addProperty("service", "translator-app");
            json.addProperty("version", "2.0.0");
            json.addProperty("uptime_seconds", uptimeSec);

            JsonObject heapObj = new JsonObject();
            heapObj.addProperty("used_mb", heap.getUsed() / (1024 * 1024));
            heapObj.addProperty("max_mb", heap.getMax() / (1024 * 1024));
            json.add("heap", heapObj);

            JsonObject cacheObj = new JsonObject();
            cacheObj.addProperty("size", cacheStats.size());
            cacheObj.addProperty("hits", cacheStats.hits());
            cacheObj.addProperty("misses", cacheStats.misses());
            cacheObj.addProperty("hit_rate", Math.round(cacheStats.hitRate() * 10000.0) / 100.0);
            json.add("cache", cacheObj);

            var providerArray = new com.google.gson.JsonArray();
            for (var ph : service.providerHealth()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", ph.name());
                p.addProperty("circuit_breaker", ph.circuitBreakerState());
                p.addProperty("failure_rate", ph.failureRate());
                providerArray.add(p);
            }
            json.add("providers", providerArray);

            resp.getWriter().write(GSON.toJson(json));
        }
    }

    // ======== Inner Servlet: Readiness ========

    static class ReadinessServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json; charset=UTF-8");
            if (READY.get()) {
                resp.setStatus(200);
                resp.getWriter().write("{\"status\":\"READY\"}");
            } else {
                resp.setStatus(503);
                resp.getWriter().write("{\"status\":\"NOT_READY\"}");
            }
        }
    }

    // ======== Inner Servlet: Prometheus Metrics ========

    static class PrometheusMetricsServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/plain; version=0.0.4; charset=UTF-8");
            resp.setStatus(200);
            resp.getWriter().write(MetricsRegistry.scrape());
        }
    }

    // ======== Inner Filter: Security Headers ========

    static class SecurityFilter implements jakarta.servlet.Filter {
        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                            jakarta.servlet.FilterChain chain) throws IOException, jakarta.servlet.ServletException {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("X-Frame-Options", "DENY");
            resp.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            resp.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            resp.setHeader("Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data:; " +
                    "connect-src 'self'");
            chain.doFilter(request, response);
        }
    }

    // ======== Inner Filter: Rate Limiter ========

    static class RateLimitFilter implements jakarta.servlet.Filter {
        private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);
        private final int maxRequestsPerMinute;
        private final Map<String, RateEntry> clients = new ConcurrentHashMap<>();

        RateLimitFilter(int maxRequestsPerMinute) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response,
                            jakarta.servlet.FilterChain chain) throws IOException, jakarta.servlet.ServletException {
            if (maxRequestsPerMinute <= 0) { chain.doFilter(request, response); return; }

            HttpServletRequest req = (HttpServletRequest) request;
            String clientIp = getClientIp(req);
            long now = System.currentTimeMillis();

            RateEntry entry = clients.compute(clientIp, (key, existing) -> {
                if (existing == null || now - existing.windowStart > 60_000) {
                    return new RateEntry(now, new AtomicInteger(1));
                }
                existing.count.incrementAndGet();
                return existing;
            });

            if (clients.size() > 10_000) {
                clients.entrySet().removeIf(e -> now - e.getValue().windowStart > 120_000);
            }

            if (entry.count.get() > maxRequestsPerMinute) {
                MetricsRegistry.rateLimitHits().increment();
                LOG.warn("Rate limit exceeded for IP: {}", clientIp);
                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setHeader("Retry-After", "60");
                ErrorHandler.sendError(resp, 429, "Too many requests. Please try again later.");
                return;
            }

            chain.doFilter(request, response);
        }

        private String getClientIp(HttpServletRequest req) {
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }

        record RateEntry(long windowStart, AtomicInteger count) {}
    }
}
