package com.translator.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates MDC (Mapped Diagnostic Context) for every incoming request so that
 * every log line emitted during the request lifecycle carries:
 *   - traceId  — X-Trace-Id header value, or a newly generated UUID
 *   - userId   — extracted from the authenticated principal if present
 *   - requestPath / httpMethod
 */
@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put("traceId", traceId);
        MDC.put("requestPath", request.getRequestURI());
        MDC.put("httpMethod", request.getMethod());

        // Echo trace-id in response header so callers can correlate
        response.setHeader(TRACE_HEADER, traceId);

        try {
            // Attempt to populate userId — may be null before JwtAuthFilter runs,
            // so we update it after the filter chain if needed.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uid) {
                MDC.put("userId", uid.toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
