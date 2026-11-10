package com.tripdiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripdiary.common.logging.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** アプリ全体の起動・共通基盤（DB マイグレーション / ヘルスチェック / Swagger / 認証 / requestId）の結合テスト。 */
@IntegrationTest
class ApplicationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway により設計書どおりの10テーブルが作成される")
    void flywayCreatesAllTables() {
        var tables =
                jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                        String.class);

        assertThat(tables)
                .containsExactlyInAnyOrder(
                        "users",
                        "refresh_tokens",
                        "spots",
                        "spot_photos",
                        "area_tags",
                        "spot_area_tags",
                        "spot_likes",
                        "comments",
                        "follows",
                        "user_spot_statuses");
    }

    @Test
    @DisplayName("ヘルスチェックは認証なしで 200 UP を返す（DB 接続を含む）")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("OpenAPI 仕様 JSON が取得でき、JWT の認証方式が定義されている")
    void openApiDocsAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TripDiary API"))
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    @DisplayName("Swagger UI が認証なしで表示できる")
    void swaggerUiAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }

    @Test
    @DisplayName("認証が必要な API に未認証でアクセスすると共通形式の 401 を返す")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me").header(RequestIdFilter.HEADER, "it-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("it-401"));
    }

    @Test
    @DisplayName("すべてのレスポンスに X-Request-Id ヘッダが付与される")
    void responseHasRequestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "it-req-1"))
                .andExpect(header().string(RequestIdFilter.HEADER, "it-req-1"));
    }

    @Test
    @DisplayName("許可したオリジンからの CORS プリフライトは成功する")
    void corsPreflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(
                        options("/api/v1/spots")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("許可していないオリジンからの CORS プリフライトは拒否される")
    void corsPreflightFromUnknownOriginRejected() throws Exception {
        mockMvc.perform(
                        options("/api/v1/spots")
                                .header("Origin", "https://evil.example.com")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
