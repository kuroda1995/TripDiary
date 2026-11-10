package com.tripdiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tripdiary.common.logging.MdcKeys;
import com.tripdiary.common.logging.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 本番プロファイル（application-prod.yml）のログ・公開設定の結合テスト（docs/rules.md 5章）。
 *
 * <p>Logback は JVM 内で一度しか初期化されず出力形式はテストの実行順に左右されるため、 出力文字列ではなく ログイベントと MDC の内容、および構造化ログの設定値を検証する。
 */
@IntegrationTest
@ActiveProfiles("prod")
class StructuredLoggingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired Environment environment;

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("本番プロファイルでは JSON（Logstash 形式）の構造化ログにアプリ名・環境名を付けて出力する設定になっている")
    void prodProfileUsesStructuredLogging() {
        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo("logstash");
        assertThat(environment.getProperty("logging.structured.json.add.app"))
                .isEqualTo("tripdiary-api");
        assertThat(environment.getProperty("logging.structured.json.add.env"))
                .isEqualTo("production");
    }

    @Test
    @DisplayName("アクセスログは INFO で出力され、MDC に requestId が入っている")
    void accessLogHasRequestIdInMdc() throws Exception {
        mockMvc.perform(get("/api/v1/me").header(RequestIdFilter.HEADER, "log-test-123"));

        ILoggingEvent accessLog =
                appender.list.stream()
                        .filter(e -> e.getFormattedMessage().startsWith("request completed"))
                        .findFirst()
                        .orElseThrow();
        assertThat(accessLog.getLevel().toString()).isEqualTo("INFO");
        assertThat(accessLog.getFormattedMessage())
                .contains("method=GET path=/api/v1/me status=401");
        assertThat(accessLog.getMDCPropertyMap()).containsEntry(MdcKeys.REQUEST_ID, "log-test-123");
    }

    @Test
    @DisplayName("Actuator へのアクセスはアクセスログに出力しない")
    void actuatorAccessIsNotLogged() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().startsWith("request completed"));
    }

    @Test
    @DisplayName("本番プロファイルでは Swagger UI と API 仕様 JSON を公開しない")
    void swaggerDisabledInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().is4xxClientError());
    }
}
