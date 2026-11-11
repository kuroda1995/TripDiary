package com.tripdiary.auth;

import com.tripdiary.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ログイン試行回数の制限（docs/requirements.md NFR-SEC-10: 同一IPから5分間に10回まで）。
 *
 * <p>固定ウィンドウ方式。カウンタは API サーバーのメモリ上にあるため、複数台構成では 1台あたりの上限になる。 厳密な制限が必要になった場合は共有ストア（DB 等）へ移す。
 */
@Slf4j
@Component
public class LoginRateLimiter {

    /** この件数を超えたら期限切れのエントリを掃除する（メモリの肥大化防止）。 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant start, int count) {}

    public LoginRateLimiter(
            Clock clock,
            @Value("${app.auth.login-rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${app.auth.login-rate-limit.window:5m}") Duration window) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /**
     * 試行を1回記録し、上限を超えていれば例外を投げる。
     *
     * @param key 制限の単位（クライアント IP）
     * @throws TooManyRequestsException 上限超過
     */
    public void check(String key) {
        check(key, "ログインの試行回数が多すぎます。しばらく時間をおいてお試しください");
    }

    /**
     * 試行を1回記録し、上限を超えていれば指定のメッセージで例外を投げる（ログイン以外のパスワード照合にも使う）。
     *
     * @param key 制限の単位（例: クライアント IP、{@code password-change:{userId}}）
     */
    public void check(String key, String message) {
        Instant now = clock.instant();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> isExpired(w, now));
        }
        Window current =
                windows.compute(
                        key,
                        (k, w) ->
                                (w == null || isExpired(w, now))
                                        ? new Window(now, 1)
                                        : new Window(w.start(), w.count() + 1));
        if (current.count() > maxAttempts) {
            log.warn("Rate limit exceeded attempts={}", current.count());
            throw new TooManyRequestsException(message);
        }
    }

    private boolean isExpired(Window w, Instant now) {
        return !now.isBefore(w.start().plus(window));
    }
}
