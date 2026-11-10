package com.tripdiary.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）の共通定義（docs/rules.md 3.1）。
 *
 * <p>Swagger UI: {@code /swagger-ui/index.html}、仕様 JSON: {@code /v3/api-docs}。 認証が必要な API には
 * {@code @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)} を付ける。
 */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "TripDiary API",
                        version = "v1",
                        description = "旅行記録・SNSアプリ TripDiary の REST API"))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
