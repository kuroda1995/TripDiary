package com.tripdiary.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * すべてのリクエストに追跡用の requestId を割り当て、MDC とレスポンスヘッダに設定する。
 *
 * <p>Spring Security より前に動くよう最優先で登録する。リクエスト終了時にアクセスログを出力し、 MDC をクリアする（後続のフィルタが設定した userId もここで消える）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** クライアント指定の ID はログ汚染を防ぐため、英数字・ハイフン・アンダースコアの64文字以内のみ受け付ける。 */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        long start = System.nanoTime();
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (shouldLogAccess(request)) {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                log.info(
                        "request completed method={} path={} status={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMs);
            }
            MDC.clear();
        }
    }

    static String resolveRequestId(String headerValue) {
        if (headerValue != null && VALID_REQUEST_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }

    /** ALB のヘルスチェック等でログが埋もれないよう、Actuator へのアクセスはログに出さない。 */
    private static boolean shouldLogAccess(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/actuator");
    }
}
