package com.tripdiary.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS で許可するオリジン（docs/requirements.md NFR-SEC-08）。
 *
 * @param allowedOrigins 許可するオリジンの一覧（例: {@code http://localhost:5173}）
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
