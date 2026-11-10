package com.tripdiary.auth;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * リフレッシュトークンを入れる Cookie。
 *
 * <p>HttpOnly（JavaScript から読めない＝XSS で盗まれない）、SameSite=Strict（CSRF 対策）、 Path を認証 API に限定して、他の API
 * リクエストには送られないようにする。
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private final JwtProperties properties;

    public ResponseCookie create(String value, Duration maxAge) {
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
