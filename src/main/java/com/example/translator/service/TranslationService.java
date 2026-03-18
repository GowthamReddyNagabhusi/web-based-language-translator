package com.example.translator.service;

import com.example.translator.config.AppConfig;
import com.example.translator.metrics.MetricsRegistry;
import com.example.translator.provider.TranslationException;
import com.example.translator.provider.TranslationProvider;
import com.example.translator.provider.TranslationResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Core translation service with:
 * - Caffeine caching (LRU, TTL-based)
 * - Resilience4j circuit breakers per provider
 * - Cascading fallback across providers
 * - Micrometer metrics instrumentation
 */
public class TranslationService {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationService.class);

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "fr", "es", "de", "hi", "ja", "zh", "it", "te"
    );

    private final List<TranslationProvider> providers;
    private final Cache<String, TranslationResult> cache;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public TranslationService(List<TranslationProvider> providers) {
        this.providers = List.copyOf(providers);

        this.cache = Caffeine.newBuilder()
                .maximumSize(AppConfig.CACHE_MAX_SIZE)
                .expireAfterWrite(AppConfig.CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(AppConfig.CIRCUIT_BREAKER_FAILURE_THRESHOLD)
                .waitDurationInOpenState(Duration.ofSeconds(AppConfig.CIRCUIT_BREAKER_WAIT_SECONDS))
                .slidingWindowSize(20)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(cbConfig);
    }

    public static boolean isValidLanguage(String lang) {
        return lang != null && SUPPORTED_LANGUAGES.contains(lang.toLowerCase().trim());
    }

    public static Set<String> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public TranslationResult translate(String text, String targetLang) throws TranslationException {
        if (text == null || text.isBlank()) {
            throw new TranslationException("Text cannot be empty", false);
        }
        if (targetLang == null || targetLang.isBlank()) {
            throw new TranslationException("Target language is required", false);
        }
        if (text.length() > AppConfig.MAX_TEXT_LENGTH) {
            throw new TranslationException("Text exceeds maximum length of " + AppConfig.MAX_TEXT_LENGTH, false);
        }
        if (!isValidLanguage(targetLang)) {
            throw new TranslationException("Unsupported language: " + targetLang, false);
        }

        String cacheKey = targetLang.toLowerCase() + ":" + text;

        // Check cache
        TranslationResult cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            MetricsRegistry.cacheHits().increment();
            LOG.debug("Cache hit for lang={}, textLen={}", targetLang, text.length());
            return cached;
        }
        MetricsRegistry.cacheMisses().increment();

        // Try providers in order with circuit breakers
        TranslationException lastException = null;
        for (TranslationProvider provider : providers) {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider.name());

            if (cb.getState() == CircuitBreaker.State.OPEN) {
                LOG.debug("Circuit breaker OPEN for provider={}, skipping", provider.name());
                continue;
            }

            Timer.Sample sample = Timer.start(MetricsRegistry.registry());
            try {
                TranslationResult result = cb.executeCheckedSupplier(
                        () -> provider.translate(text, targetLang.toLowerCase())
                );

                sample.stop(MetricsRegistry.translationLatency(provider.name()));
                MetricsRegistry.translationSuccess(provider.name()).increment();

                // Store in cache
                cache.put(cacheKey, result);
                LOG.info("Translation via provider={}, lang={}, textLen={}", provider.name(), targetLang, text.length());
                return result;

            } catch (Throwable e) {
                sample.stop(MetricsRegistry.translationLatency(provider.name()));
                MetricsRegistry.translationFailure(provider.name()).increment();
                LOG.warn("Provider {} failed: {}", provider.name(), e.getMessage());
                lastException = (e instanceof TranslationException te) ? te
                        : new TranslationException("Provider " + provider.name() + " failed", e, true);
            }
        }

        throw lastException != null ? lastException
                : new TranslationException("All translation providers unavailable", true);
    }

    /** Cache stats for health/metrics endpoints. */
    public CacheStats cacheStats() {
        var stats = cache.stats();
        return new CacheStats(
                cache.estimatedSize(),
                stats.hitCount(),
                stats.missCount(),
                stats.hitRate()
        );
    }

    /** Provider circuit breaker states for health endpoint. */
    public List<ProviderHealth> providerHealth() {
        return providers.stream().map(p -> {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(p.name());
            return new ProviderHealth(p.name(), cb.getState().name(), cb.getMetrics().getFailureRate());
        }).toList();
    }

    public record CacheStats(long size, long hits, long misses, double hitRate) {}
    public record ProviderHealth(String name, String circuitBreakerState, float failureRate) {}
}
