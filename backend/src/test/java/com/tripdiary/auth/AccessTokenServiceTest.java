package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripdiary.support.MutableClock;
import com.tripdiary.support.TestUsers;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private final JwtConfig config = new JwtConfig();
    private final MutableClock clock = MutableClock.at("2026-11-11T00:00:00Z");

    private AccessTokenService service(byte[] secret, String issuer) {
        JwtProperties props =
                new JwtProperties(
                        Base64.getEncoder().encodeToString(secret),
                        issuer,
                        Duration.ofMinutes(15),
                        Duration.ofDays(14),
                        true);
        SecretKey key = config.jwtSecretKey(props);
        return new AccessTokenService(
                config.jwtEncoder(key), config.jwtDecoder(key, props), props, clock);
    }

    private static byte[] secret(int seed) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) seed);
        return bytes;
    }

    private final AccessTokenService service = service(secret(1), "tripdiary-api");

    @Test
    @DisplayName("発行したトークンを検証するとユーザーIDとユーザー名が復元できる")
    void issueAndVerify() {
        var issued = service.issue(TestUsers.user(42L, "taro_trip"));

        AuthenticatedUser user = service.verify(issued.value());

        assertThat(user).isEqualTo(new AuthenticatedUser(42L, "taro_trip"));
        assertThat(issued.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("有効期限（15分）の直前は有効、期限を過ぎると TOKEN_EXPIRED")
    void expiresAfter15Minutes() {
        String token = service.issue(TestUsers.user(1L, "alice")).value();

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(service.verify(token).id()).isEqualTo(1L);

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(TokenAuthenticationException.class)
                .extracting("code")
                .isEqualTo(TokenAuthenticationException.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("署名を改ざんしたトークンは INVALID_TOKEN")
    void tamperedTokenIsInvalid() {
        String token = service.issue(TestUsers.user(1L, "alice")).value();
        String tampered =
                token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertInvalid(tampered);
    }

    @Test
    @DisplayName("別の署名鍵で作られたトークンは INVALID_TOKEN")
    void tokenSignedWithOtherKeyIsInvalid() {
        String foreign =
                service(secret(2), "tripdiary-api").issue(TestUsers.user(1L, "alice")).value();

        assertInvalid(foreign);
    }

    @Test
    @DisplayName("発行者（iss）が異なるトークンは INVALID_TOKEN")
    void tokenWithOtherIssuerIsInvalid() {
        String foreign =
                service(secret(1), "someone-else").issue(TestUsers.user(1L, "alice")).value();

        assertInvalid(foreign);
    }

    @Test
    @DisplayName("JWT の形式でない文字列は INVALID_TOKEN")
    void garbageIsInvalid() {
        assertInvalid("not-a-jwt");
        assertInvalid("");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(TokenAuthenticationException.class)
                .extracting("code")
                .isEqualTo(TokenAuthenticationException.INVALID_TOKEN);
    }
}
