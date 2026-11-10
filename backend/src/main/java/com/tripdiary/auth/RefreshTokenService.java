package com.tripdiary.auth;

import com.tripdiary.common.exception.UnauthorizedException;
import com.tripdiary.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・ローテーション・失効（docs/features/01_auth.md 5章）。
 *
 * <p>トークン本体は推測困難な乱数で、DB には SHA-256 ハッシュのみを保存する。 使用済み（失効済み）のトークンが再利用された場合は盗用とみなし、
 * そのユーザーの全トークンを失効させる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;

    public record IssuedRefreshToken(String value, java.time.Duration ttl) {}

    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(raw), expiresAt));
        return new IssuedRefreshToken(raw, properties.refreshTokenTtl());
    }

    /**
     * トークンを検証して失効させ、持ち主のユーザーを返す（呼び出し側で新しいトークンを発行する）。
     *
     * <p>再利用検知時の全トークン失効を確定させるため、例外時もロールバックしない。
     *
     * @throws UnauthorizedException 存在しない・期限切れ・失効済みのトークン
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRefreshToken();
        }
        RefreshToken token =
                repository.findByTokenHash(hash(rawToken)).orElseThrow(this::invalidRefreshToken);
        Instant now = clock.instant();
        User user = token.getUser();
        if (token.isRevoked()) {
            int revoked = repository.revokeAllByUserId(user.getId(), now);
            log.warn(
                    "Refresh token reuse detected. Revoked all tokens userId={} count={}",
                    user.getId(),
                    revoked);
            throw invalidRefreshToken();
        }
        if (token.isExpired(now)) {
            throw invalidRefreshToken();
        }
        token.revoke(now);
        return user;
    }

    /** ログアウト時の失効。存在しない・失効済みのトークンでもエラーにしない（冪等）。 */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(now));
    }

    /** パスワード変更時などに、ユーザーの全トークンを失効させる。 */
    @Transactional
    public void revokeAll(long userId) {
        repository.revokeAllByUserId(userId, clock.instant());
    }

    static String hash(String rawToken) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private UnauthorizedException invalidRefreshToken() {
        return new UnauthorizedException(INVALID_REFRESH_TOKEN, "ログインの有効期限が切れました。もう一度ログインしてください");
    }
}
