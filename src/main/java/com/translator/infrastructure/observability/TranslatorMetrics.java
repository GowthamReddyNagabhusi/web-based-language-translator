package com.translator.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all custom Micrometer metrics.
 * Injected into services to record translation, auth, and cache events.
 */
@Component
public class TranslatorMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public TranslatorMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ── Translation metrics ──────────────────────────────────────────────────

    /**
     * Increment the total translations counter, tagged by language pair and provider.
     */
    public void recordTranslationRequest(String targetLanguage, String provider, boolean cached) {
        counterKey("translation.requests.total",
                "target_language", targetLanguage,
                "provider", provider,
                "cached", String.valueOf(cached)).increment();
    }

    /**
     * Record end-to-end latency of a translation operation tagged by provider.
     */
    public Timer translationLatencyTimer(String provider) {
        return Timer.builder("translation.latency")
                .description("End-to-end translation latency per provider")
                .tag("provider", provider)
                .register(registry);
    }

    // ── Auth metrics ─────────────────────────────────────────────────────────

    public void recordLoginAttempt(boolean success) {
        counterKey("auth.login.attempts", "success", String.valueOf(success)).increment();
    }

    // ── Cache metrics ─────────────────────────────────────────────────────────

    public void recordL1CacheHit(boolean hit) {
        counterKey("cache.hit", "level", "L1", "result", hit ? "hit" : "miss").increment();
    }

    public void recordL2CacheHit(boolean hit) {
        counterKey("cache.hit", "level", "L2", "result", hit ? "hit" : "miss").increment();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Counter counterKey(String name, String... tags) {
        // Build a stable cache key from name+tags
        String key = name + ":" + String.join(":", tags);
        return counterCache.computeIfAbsent(key, k -> Counter.builder(name)
                .tags(tags)
                .register(registry));
    }
}
