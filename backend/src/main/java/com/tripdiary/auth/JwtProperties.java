package com.tripdiary.auth;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT の設定（docs/requirements.md NFR-SEC-03 / NFR-SEC-04）。
 *
 * @param secret HS256 の署名鍵（Base64、32バイト以上）。本番は環境変数 JWT_SECRET で渡す
 * @param issuer 発行者（iss クレーム）
 * @param accessTokenTtl アクセストークンの有効期間（15分）
 * @param refreshTokenTtl リフレッシュトークンの有効期間（14日）
 * @param refreshCookieSecure リフレッシュトークン Cookie に Secure 属性を付けるか（HTTPS 以外のローカル開発のみ false）
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean refreshCookieSecure) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret を設定してください");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("app.jwt.secret は Base64 で指定してください", e);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret は " + MIN_SECRET_BYTES + " バイト以上にしてください");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.jwt.issuer を設定してください");
        }
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            throw new IllegalArgumentException("app.jwt.access-token-ttl は正の値にしてください");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalArgumentException("app.jwt.refresh-token-ttl は正の値にしてください");
        }
    }

    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret);
    }

    /** 署名鍵をログ等に出さない。 */
    @Override
    public String toString() {
        return "JwtProperties{issuer="
                + issuer
                + ", accessTokenTtl="
                + accessTokenTtl
                + ", refreshTokenTtl="
                + refreshTokenTtl
                + "}";
    }
}
