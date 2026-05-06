package com.translator.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.ListLanguagesRequest;

/**
 * Custom actuator health indicator that checks:
 *   - Redis connectivity
 *   - AWS Translate reachability (via lightweight ListLanguages call)
 */
@Component("translationProviders")
public class TranslationProviderHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;
    private final TranslateClient translateClient;

    public TranslationProviderHealthIndicator(StringRedisTemplate redisTemplate,
                                              TranslateClient translateClient) {
        this.redisTemplate = redisTemplate;
        this.translateClient = translateClient;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // Redis check
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            builder.withDetail("redis", "UP — " + pong);
        } catch (Exception e) {
            builder.down().withDetail("redis", "DOWN — " + e.getMessage());
        }

        // AWS Translate check
        try {
            translateClient.listLanguages(ListLanguagesRequest.builder().maxResults(1).build());
            builder.withDetail("awsTranslate", "UP");
        } catch (Exception e) {
            // Degraded, not fatal — falling back to LibreTranslate is acceptable
            builder.withDetail("awsTranslate", "DEGRADED — " + e.getMessage());
        }

        return builder.build();
    }
}
