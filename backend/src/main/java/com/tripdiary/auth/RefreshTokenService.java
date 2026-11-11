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
 * <p>トークン本体は推測困難な乱数で、DB には SHA-256 ハッシュのみを保存する。 失効済みのトークン、またはローテーションで使用済みになってから猶予期間 （{@link
 * JwtProperties#refreshReuseGrace()}）を過ぎたトークンが再利用された場合は盗用とみなし、そのユーザーの全トークンを失効させる。
 * 猶予期間内の再利用は、更新の応答を受け取る前の画面遷移や複数タブでの同時更新とみなして許す。
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
     * トークンを検証して使用済みにし、持ち主のユーザーを返す（呼び出し側で新しいトークンを発行する）。
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
        if (token.isRotated()) {
            Instant graceEnd = token.getRotatedAt().plus(properties.refreshReuseGrace());
            if (now.isBefore(graceEnd)) {
                // 更新の応答を受け取る前に画面を移った・複数タブで同時に更新した場合（docs/features/01_auth.md 5章）
                log.info("Refresh token reused within grace period userId={}", user.getId());
                return user;
            }
            int revoked = repository.revokeAllByUserId(user.getId(), now);
            log.warn(
                    "Refresh token reuse detected after grace period. Revoked all tokens userId={} count={}",
                    user.getId(),
                    revoked);
            throw invalidRefreshToken();
        }
        token.markRotated(now);
        return user;
    }

    /**
     * ログアウト時の失効。存在しない・失効済みのトークンでもエラーにしない（冪等）。
     *
     * <p>猶予期間中の使用済みトークン（直前の更新で交換した古いトークンなど）も失効させ、ログアウト後に再びログインできないようにする。
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        repository
                .findByTokenHash(hash(rawToken))
                .ifPresent(
                        token -> {
                            token.revoke(now);
                            repository.revokeRecentlyRotated(
                                    token.getUser().getId(),
                                    now.minus(properties.refreshReuseGrace()),
                                    now);
                        });
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
