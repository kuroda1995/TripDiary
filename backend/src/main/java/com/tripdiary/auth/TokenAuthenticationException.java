package com.tripdiary.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * アクセストークンの検証エラー。GlobalExceptionHandler がエラーコード付きの 401 に変換する。
 *
 * <p>期限切れ（{@code TOKEN_EXPIRED}）の場合、フロントエンドはトークンを更新してリトライする。
 */
public class TokenAuthenticationException extends AuthenticationException {

    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    private final String code;

    private TokenAuthenticationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static TokenAuthenticationException expired() {
        return new TokenAuthenticationException(TOKEN_EXPIRED, "アクセストークンの有効期限が切れています");
    }

    public static TokenAuthenticationException invalid() {
        return new TokenAuthenticationException(INVALID_TOKEN, "アクセストークンが不正です");
    }

    public String getCode() {
        return code;
    }
}
