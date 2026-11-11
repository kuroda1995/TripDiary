package com.tripdiary.common.config;

import com.tripdiary.auth.AccessTokenService;
import com.tripdiary.auth.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Spring Security の設定。
 *
 * <p>JWT によるステートレス認証のため、セッション・CSRF・フォームログインを無効化する。 （リフレッシュトークン Cookie は SameSite=Strict で CSRF
 * を防ぐ。） 認証 / 認可エラーは {@code GlobalExceptionHandler} に委譲し、共通エラーレスポンスで返す。
 */
@Configuration
public class SecurityConfig {

    /** 認証なしでアクセスできるパス（ヘルスチェック・API 仕様書）。 */
    static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/health/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error"
    };

    /** 認証なしで呼べる認証 API。 */
    static final String[] PUBLIC_AUTH_PATHS = {
        "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
    };

    /**
     * 未ログインでも閲覧できる API（GET のみ）。
     *
     * <p>トークンが付いていれば認証済みとして扱う（本人の非公開スポットの閲覧などに使う）。
     */
    static final String[] PUBLIC_READ_PATHS = {
        "/api/v1/spots",
        "/api/v1/spots/*",
        "/api/v1/spots/*/comments",
        "/api/v1/areas",
        "/api/v1/users/*",
        "/api/v1/users/*/spots",
        "/api/v1/users/*/following",
        "/api/v1/users/*/followers"
    };

    /** ログイン中のユーザー本人の API。{@link #PUBLIC_READ_PATHS} の {@code /users/*} より先に判定する。 */
    static final String[] ME_PATHS = {"/api/v1/users/me", "/api/v1/users/me/**"};

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            AccessTokenService accessTokenService)
            throws Exception {
        AuthenticationEntryPoint entryPoint =
                (request, response, ex) -> resolver.resolveException(request, response, null, ex);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS)
                                        .permitAll()
                                        .requestMatchers(ME_PATHS)
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, PUBLIC_READ_PATHS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(accessTokenService, entryPoint),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(entryPoint)
                                        .accessDeniedHandler(
                                                (request, response, ex) ->
                                                        resolver.resolveException(
                                                                request, response, null, ex)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
