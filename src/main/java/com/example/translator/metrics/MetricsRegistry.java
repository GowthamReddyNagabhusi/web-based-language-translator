package com.example.translator.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Centralized metrics registry backed by Micrometer + Prometheus.
 * Tracks translation latency, throughput, cache efficiency, and provider health.
 */
public final class MetricsRegistry {

    private static final PrometheusMeterRegistry REGISTRY = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    static {
        new JvmMemoryMetrics().bindTo(REGISTRY);
        new JvmGcMetrics().bindTo(REGISTRY);
        new JvmThreadMetrics().bindTo(REGISTRY);
        new ClassLoaderMetrics().bindTo(REGISTRY);
        new ProcessorMetrics().bindTo(REGISTRY);
        new UptimeMetrics().bindTo(REGISTRY);
    }

    private MetricsRegistry() {}

    public static MeterRegistry registry() { return REGISTRY; }

    /** Scrape endpoint content for Prometheus. */
    public static String scrape() { return REGISTRY.scrape(); }

    // ---- Pre-built meters ----

    public static Timer translationLatency(String provider) {
        return Timer.builder("translation.latency")
                .tag("provider", provider)
                .description("Translation request latency")
                .publishPercentileHistogram()
                .register(REGISTRY);
    }

    public static Counter translationSuccess(String provider) {
        return Counter.builder("translation.success")
                .tag("provider", provider)
                .description("Successful translations")
                .register(REGISTRY);
    }

    public static Counter translationFailure(String provider) {
        return Counter.builder("translation.failure")
                .tag("provider", provider)
                .description("Failed translations")
                .register(REGISTRY);
    }

    public static Counter cacheHits() {
        return Counter.builder("translation.cache.hits")
                .description("Translation cache hit count")
                .register(REGISTRY);
    }

    public static Counter cacheMisses() {
        return Counter.builder("translation.cache.misses")
                .description("Translation cache miss count")
                .register(REGISTRY);
    }

    public static Counter rateLimitHits() {
        return Counter.builder("ratelimit.exceeded")
                .description("Requests rejected by rate limiter")
                .register(REGISTRY);
    }

    public static Counter httpRequests(String method, String path, int status) {
        return Counter.builder("http.requests")
                .tag("method", method)
                .tag("path", path)
                .tag("status", String.valueOf(status))
                .register(REGISTRY);
    }

    public static Timer httpLatency(String method, String path) {
        return Timer.builder("http.latency")
                .tag("method", method)
                .tag("path", path)
                .publishPercentileHistogram()
                .register(REGISTRY);
    }
}
