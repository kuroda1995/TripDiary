package com.tripdiary.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("X-Request-Id が無い場合は UUID を生成してレスポンスヘッダに設定する")
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/spots"),
                response,
                new MockFilterChain());

        String requestId = response.getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    @DisplayName("正しい形式の X-Request-Id はそのまま引き継ぐ")
    void reusesValidRequestIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/spots");
        request.addHeader(RequestIdFilter.HEADER, "abc-123_XYZ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123_XYZ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "has space",
                "line\nbreak",
                "<script>",
                "x12345678901234567890123456789012345678901234567890123456789012345"
            })
    @DisplayName("不正な形式・65文字以上の X-Request-Id は採用せず新しい ID を生成する")
    void rejectsInvalidRequestId(String headerValue) {
        String resolved = RequestIdFilter.resolveRequestId(headerValue);

        assertThat(resolved).isNotEqualTo(headerValue);
        assertThat(UUID.fromString(resolved)).isNotNull();
    }

    @Test
    @DisplayName("後続処理の実行中は MDC に requestId が入り、終了後は MDC がクリアされる")
    void putsRequestIdIntoMdcDuringChainAndClearsAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/spots");
        request.addHeader(RequestIdFilter.HEADER, "req-1");
        AtomicReference<String> seenRequestId = new AtomicReference<>();
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        seenRequestId.set(MDC.get(MdcKeys.REQUEST_ID));
                        // 後続フィルタ（JWT 認証）が userId を設定するケースを模擬
                        MDC.put(MdcKeys.USER_ID, "42");
                    }
                };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenRequestId.get()).isEqualTo("req-1");
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }

    @Test
    @DisplayName("後続処理で例外が発生しても MDC はクリアされる")
    void clearsMdcEvenWhenChainThrows() {
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        throw new IllegalStateException("boom");
                    }
                };
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/spots");

        try {
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        } catch (Exception ignored) {
            // 例外の伝播自体は期待どおり
        }

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
