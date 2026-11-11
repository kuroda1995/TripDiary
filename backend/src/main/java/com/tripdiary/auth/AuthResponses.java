package com.tripdiary.auth;

import com.tripdiary.auth.AuthService.AuthResult;
import com.tripdiary.auth.dto.AuthResponse;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.user.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * トークンを発行したときのレスポンス（本文にアクセストークン、Cookie にリフレッシュトークン）。
 *
 * <p>新規登録・ログイン・トークン更新・パスワード変更で共通。
 */
@Component
@RequiredArgsConstructor
public class AuthResponses {

    private final RefreshTokenCookies cookies;
    private final PhotoStorage photoStorage;

    public ResponseEntity<AuthResponse> of(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookies.create(result.refreshToken().value(), result.refreshToken().ttl())
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                        AuthResponse.bearer(
                                result.accessToken().value(),
                                result.accessToken().expiresInSeconds(),
                                MeResponse.from(result.user(), photoStorage::publicUrl)));
    }
}
