package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripdiary.common.exception.TooManyRequestsException;
import com.tripdiary.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final MutableClock clock = MutableClock.at("2026-11-11T00:00:00Z");
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 10, Duration.ofMinutes(5));

    private void attempt(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.check(ip);
        }
    }

    @Test
    @DisplayName("同一 IP から5分間に10回までは許可し、11回目は TOO_MANY_REQUESTS")
    void blocksEleventhAttempt() {
        attempt("1.1.1.1", 10);

        assertThatThrownBy(() -> limiter.check("1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting("code")
                .isEqualTo(TooManyRequestsException.CODE);
    }

    @Test
    @DisplayName("IP ごとに独立して数える")
    void countsPerIp() {
        attempt("1.1.1.1", 10);

        assertThatCode(() -> limiter.check("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("5分経過するとカウントがリセットされる")
    void resetsAfterWindow() {
        attempt("1.1.1.1", 10);

        clock.advance(Duration.ofMinutes(5));

        assertThatCode(() -> attempt("1.1.1.1", 10)).doesNotThrowAnyException();
    }
}
