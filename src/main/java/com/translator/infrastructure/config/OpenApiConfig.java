package com.translator.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration.
 *
 * Accessible via:
 *   - Swagger UI: /swagger-ui.html
 *   - Raw spec:   /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI translatorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Language Translator API")
                        .description("Production-grade Language Translator — supports 75+ languages via AWS Translate " +
                                "with Redis/Caffeine caching, JWT authentication, history, bulk translation, and S3 export.")
                        .version("v2.0.0")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("GitHub Repository")
                        .url("https://github.com/GowthamReddyNagabhusi/web-based-language-translator"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Provide the JWT access token obtained from POST /api/v1/auth/login")));
    }
}
