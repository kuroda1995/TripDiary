package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private static JwtProperties props(String secret) {
        return new JwtProperties(
                secret, "tripdiary-api", Duration.ofMinutes(15), Duration.ofDays(14), true);
    }

    @Test
    @DisplayName("32バイト以上の Base64 の署名鍵を受け付ける")
    void acceptsValidSecret() {
        assertThat(props(VALID_SECRET).secretBytes()).hasSize(32);
    }

    @Test
    @DisplayName("署名鍵が未設定・32バイト未満・Base64 でない場合は起動時にエラーにする")
    void rejectsInvalidSecret() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> props(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> props(shortSecret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
        assertThatThrownBy(() -> props("not base64 !!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("有効期間が0以下ならエラーにする")
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(
                        () ->
                                new JwtProperties(
                                        VALID_SECRET,
                                        "iss",
                                        Duration.ZERO,
                                        Duration.ofDays(14),
                                        true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new JwtProperties(
                                        VALID_SECRET,
                                        "iss",
                                        Duration.ofMinutes(15),
                                        Duration.ofDays(-1),
                                        true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString に署名鍵を含めない")
    void toStringHidesSecret() {
        assertThat(props(VALID_SECRET).toString()).doesNotContain(VALID_SECRET);
    }
}
