package com.example.translator.api.filter;

import com.example.translator.metrics.MetricsRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Records HTTP request metrics (latency, status codes) via Micrometer. */
public class MetricsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String method = req.getMethod();
        String path = normalizePath(req.getServletPath());

        Timer.Sample sample = Timer.start(MetricsRegistry.registry());
        try {
            chain.doFilter(request, response);
        } finally {
            sample.stop(MetricsRegistry.httpLatency(method, path));
            MetricsRegistry.httpRequests(method, path, resp.getStatus()).increment();
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        // Collapse all static resource paths to a single tag to avoid cardinality explosion
        if (path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css")
                || path.endsWith(".ico") || path.endsWith(".png") || path.endsWith(".svg")) {
            return "/static";
        }
        return path;
    }
}
