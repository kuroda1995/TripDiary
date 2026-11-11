package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.UnauthorizedException;
import com.tripdiary.support.MutableClock;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final MutableClock clock = MutableClock.at("2026-11-11T00:00:00Z");
    private final JwtProperties props =
            new JwtProperties(
                    Base64.getEncoder().encodeToString(new byte[32]),
                    "tripdiary-api",
                    Duration.ofMinutes(15),
                    Duration.ofDays(14),
                    true,
                    Duration.ofSeconds(30));
    private final RefreshTokenService service = new RefreshTokenService(repository, props, clock);
    private final User user = TestUsers.user(7L, "alice");

    private RefreshToken stored(String raw, Instant expiresAt) {
        RefreshToken token = new RefreshToken(user, RefreshTokenService.hash(raw), expiresAt);
        when(repository.findByTokenHash(RefreshTokenService.hash(raw)))
                .thenReturn(Optional.of(token));
        return token;
    }

    @Test
    @DisplayName("発行時はトークン本体ではなく SHA-256 ハッシュを、有効期限14日で保存する")
    void issueStoresHashOnly() {
        var issued = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.value()))
                .isNotEqualTo(issued.value())
                .hasSize(64);
        assertThat(saved.getValue().getExpiresAt())
                .isEqualTo(clock.instant().plus(Duration.ofDays(14)));
        assertThat(issued.ttl()).isEqualTo(Duration.ofDays(14));
        // 32バイトの乱数を Base64URL（パディングなし）にすると43文字
        assertThat(issued.value()).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("発行するたびに異なるトークンになる")
    void issueGeneratesUniqueTokens() {
        assertThat(service.issue(user).value()).isNotEqualTo(service.issue(user).value());
    }

    @Test
    @DisplayName("有効なトークンでローテーションすると、そのトークンは使用済み（失効とは区別）になりユーザーが返る")
    void rotateMarksTokenRotated() {
        RefreshToken token = stored("raw-1", clock.instant().plus(Duration.ofDays(1)));

        User result = service.rotate("raw-1");

        assertThat(result).isSameAs(user);
        assertThat(token.isRotated()).isTrue();
        assertThat(token.getRotatedAt()).isEqualTo(clock.instant());
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("使用済みになってから猶予期間（30秒）内の再利用は許し、全トークン失効もしない")
    void reuseWithinGraceIsAllowed() {
        RefreshToken token = stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        service.rotate("raw-1");

        clock.advance(Duration.ofSeconds(29));
        assertThat(service.rotate("raw-1")).isSameAs(user);
        assertThat(service.rotate("raw-1")).isSameAs(user);

        verify(repository, never()).revokeAllByUserId(anyLong(), any());
        // 猶予期間の起点は最初に使用済みになった時刻のまま（再利用のたびに延びない）
        assertThat(token.getRotatedAt()).isEqualTo(clock.instant().minusSeconds(29));
    }

    @Test
    @DisplayName("猶予期間を過ぎてからの再利用は盗用とみなし、全トークンを失効させる")
    void reuseAfterGraceIsDetected() {
        stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        service.rotate("raw-1");

        clock.advance(Duration.ofSeconds(30));

        assertInvalid("raw-1");
        verify(repository).revokeAllByUserId(7L, clock.instant());
    }

    @Test
    @DisplayName("ログアウトなどで失効したトークンは、猶予期間内でも再利用を許さず全トークンを失効させる")
    void revokedTokenHasNoGrace() {
        RefreshToken token = stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        service.rotate("raw-1");
        token.revoke(clock.instant());

        clock.advance(Duration.ofSeconds(1));

        assertInvalid("raw-1");
        verify(repository).revokeAllByUserId(7L, clock.instant());
    }

    @Test
    @DisplayName("猶予期間を 0 にすると、使用済みトークンの再利用はすぐに盗用とみなす")
    void zeroGrace() {
        RefreshTokenService strict =
                new RefreshTokenService(
                        repository,
                        new JwtProperties(
                                props.secret(),
                                props.issuer(),
                                props.accessTokenTtl(),
                                props.refreshTokenTtl(),
                                true,
                                Duration.ZERO),
                        clock);
        stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        strict.rotate("raw-1");

        assertThatThrownBy(() -> strict.rotate("raw-1")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllByUserId(7L, clock.instant());
    }

    @Test
    @DisplayName("存在しない・空のトークンは INVALID_REFRESH_TOKEN")
    void rotateRejectsUnknownOrBlank() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertInvalid("unknown");
        assertInvalid(null);
        assertInvalid(" ");
    }

    @Test
    @DisplayName("期限切れのトークンは INVALID_REFRESH_TOKEN")
    void rotateRejectsExpired() {
        stored("raw-1", clock.instant());

        assertInvalid("raw-1");
    }

    @Test
    @DisplayName("失効済みトークンの再利用を検知したら、そのユーザーの全トークンを失効させる")
    void rotateDetectsReuse() {
        RefreshToken token = stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        token.revoke(clock.instant().minusSeconds(60));

        assertInvalid("raw-1");
        verify(repository).revokeAllByUserId(7L, clock.instant());
    }

    @Test
    @DisplayName("通常のローテーションでは全トークン失効は行わない")
    void normalRotateDoesNotRevokeAll() {
        stored("raw-1", clock.instant().plus(Duration.ofDays(1)));

        service.rotate("raw-1");

        verify(repository, never()).revokeAllByUserId(anyLong(), any());
    }

    @Test
    @DisplayName("ログアウト時の失効は、存在しない・空のトークンでもエラーにしない")
    void revokeIsIdempotent() {
        RefreshToken token = stored("raw-1", clock.instant().plus(Duration.ofDays(1)));
        when(repository.findByTokenHash(RefreshTokenService.hash("unknown")))
                .thenReturn(Optional.empty());

        service.revoke("raw-1");
        service.revoke("unknown");
        service.revoke(null);

        assertThat(token.isRevoked()).isTrue();
        // 猶予期間中の使用済みトークンもあわせて失効させる
        verify(repository)
                .revokeRecentlyRotated(7L, clock.instant().minusSeconds(30), clock.instant());
    }

    @Test
    @DisplayName("revokeAll はユーザーの全トークンを失効させる")
    void revokeAll() {
        service.revokeAll(7L);

        verify(repository).revokeAllByUserId(7L, clock.instant());
    }

    private void assertInvalid(String raw) {
        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("code")
                .isEqualTo(RefreshTokenService.INVALID_REFRESH_TOKEN);
    }
}
