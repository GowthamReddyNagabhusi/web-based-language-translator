package com.example.translator.config;

import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized, environment-aware configuration.
 * Priority: environment variable > config.properties > default value.
 */
public final class AppConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                PROPS.load(is);
                LOG.info("Loaded config.properties");
            }
        } catch (Exception e) {
            LOG.warn("Failed to load config.properties: {}", e.getMessage());
        }
    }

    private AppConfig() {}

    public static String get(String key, String defaultValue) {
        String envKey = key.replace('.', '_').toUpperCase();
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) return envVal.trim();
        return PROPS.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String val = get(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(val);
    }

    // Pre-resolved constants for hot paths
    public static final int SERVER_PORT = getInt("server.port", 8080);
    public static final int MAX_TEXT_LENGTH = getInt("translation.max.length", 5000);
    public static final int RATE_LIMIT_PER_MINUTE = getInt("ratelimit.requests.per.minute", 60);
    public static final int MAX_BODY_BYTES = getInt("request.max.body.bytes", 65536);
    public static final long CONNECT_TIMEOUT_SECONDS = getLong("translation.connect.timeout", 8);
    public static final long REQUEST_TIMEOUT_SECONDS = getLong("translation.request.timeout", 10);
    public static final String[] CORS_ALLOWED_ORIGINS = get("cors.allowed.origins", "*").split(",");
    public static final int CACHE_MAX_SIZE = getInt("cache.max.size", 10000);
    public static final long CACHE_TTL_MINUTES = getLong("cache.ttl.minutes", 60);
    public static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = getInt("circuit.breaker.failure.threshold", 50);
    public static final int CIRCUIT_BREAKER_WAIT_SECONDS = getInt("circuit.breaker.wait.seconds", 30);

    static {
        for (int i = 0; i < CORS_ALLOWED_ORIGINS.length; i++) {
            CORS_ALLOWED_ORIGINS[i] = CORS_ALLOWED_ORIGINS[i].trim();
        }
    }
}
