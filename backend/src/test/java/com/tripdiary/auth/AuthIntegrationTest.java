package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.common.logging.MdcKeys;
import com.tripdiary.common.logging.RequestIdFilter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 認証 API の結合テスト（docs/qa_test_plan.md 4.1）。 */
@IntegrationTest
class AuthIntegrationTest {

    /** ログイン試行制限に引っかからないよう、テストごとに異なるクライアント IP を使う。 */
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueIp() {
        int n = IP_SEQ.getAndIncrement();
        return "10.0." + (n / 250) + "." + (n % 250 + 1);
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String signupBody(String email, String username, String password) {
        return """
                {"email":"%s","username":"%s","displayName":"旅するタロウ","password":"%s"}
                """
                .formatted(email, username, password);
    }

    private MvcResult signup(String email, String username) throws Exception {
        return mockMvc.perform(
                        json(
                                post("/api/v1/auth/signup"),
                                signupBody(email, username, "Passw0rd123")))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(
                        json(
                                        post("/api/v1/auth/login"),
                                        """
                                {"email":"%s","password":"%s"}
                                """
                                                .formatted(email, password))
                                .with(
                                        req -> {
                                            req.setRemoteAddr(ip);
                                            return req;
                                        }))
                .andReturn();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookies.NAME);
        assertThat(cookie).as("refresh_token cookie").isNotNull();
        return cookie;
    }

    private MvcResult refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn();
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("新規登録")
    class Signup {

        @Test
        @DisplayName("登録すると 201 でアクセストークンとユーザーを返し、リフレッシュトークンを HttpOnly Cookie で返す")
        void signupSucceeds() throws Exception {
            MvcResult result =
                    mockMvc.perform(
                                    json(
                                            post("/api/v1/auth/signup"),
                                            signupBody(
                                                    "taro@example.com",
                                                    "taro_trip",
                                                    "Passw0rd123")))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.tokenType").value("Bearer"))
                            .andExpect(jsonPath("$.expiresIn").value(900))
                            .andExpect(jsonPath("$.user.username").value("taro_trip"))
                            .andExpect(jsonPath("$.user.email").value("taro@example.com"))
                            .andExpect(jsonPath("$.refreshToken").doesNotExist())
                            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                            .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie)
                    .contains("refresh_token=")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
                    .contains("Path=/api/v1/auth");
        }

        @Test
        @DisplayName("パスワードは BCrypt でハッシュ化して保存され、平文は保存されない")
        void passwordIsHashed() throws Exception {
            signup("taro@example.com", "taro_trip");

            String hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
            assertThat(hash).startsWith("$2a$10$").doesNotContain("Passw0rd123");
        }

        @Test
        @DisplayName("大文字小文字だけ違うメールアドレスは 409 EMAIL_ALREADY_EXISTS")
        void duplicateEmail() throws Exception {
            signup("taro@example.com", "taro_trip");

            mockMvc.perform(
                            json(
                                    post("/api/v1/auth/signup"),
                                    signupBody("TARO@example.com", "another", "Passw0rd123")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("大文字小文字だけ違うユーザー名は 409 USERNAME_ALREADY_EXISTS")
        void duplicateUsername() throws Exception {
            signup("taro@example.com", "taro_trip");

            mockMvc.perform(
                            json(
                                    post("/api/v1/auth/signup"),
                                    signupBody("other@example.com", "TARO_TRIP", "Passw0rd123")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("入力形式の誤りは 400 VALIDATION_ERROR と項目ごとのエラーを返す")
        void validationErrors() throws Exception {
            mockMvc.perform(
                            json(
                                    post("/api/v1/auth/signup"),
                                    """
                                    {"email":"not-an-email","username":"ab","displayName":"","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[?(@.field == 'email')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'username')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'displayName')]").exists())
                    .andExpect(jsonPath("$.details[?(@.field == 'password')]").exists());
        }

        @Test
        @DisplayName("パスワードは英字と数字の両方が必要で、全角文字は使えない")
        void passwordRules() throws Exception {
            for (String password : new String[] {"onlyletters", "12345678", "パスワード1234abcd"}) {
                mockMvc.perform(
                                json(
                                        post("/api/v1/auth/signup"),
                                        signupBody("p@example.com", "pw_user", password)))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.details[0].field").value("password"));
            }
        }
    }

    @Nested
    @DisplayName("ログイン")
    class Login {

        @Test
        @DisplayName("正しいメールアドレス（大文字小文字は無視）とパスワードでログインできる")
        void loginSucceeds() throws Exception {
            signup("taro@example.com", "taro_trip");

            MvcResult result = login("Taro@Example.com", "Passw0rd123", uniqueIp());

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(accessToken(result)).isNotBlank();
            refreshCookie(result);
        }

        @Test
        @DisplayName("パスワード誤りと存在しないメールアドレスは同じ 401 INVALID_CREDENTIALS")
        void invalidCredentials() throws Exception {
            signup("taro@example.com", "taro_trip");

            MvcResult wrongPassword = login("taro@example.com", "Wrong0000", uniqueIp());
            MvcResult unknownEmail = login("nobody@example.com", "Passw0rd123", uniqueIp());

            assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
            assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
            assertThat(
                            JsonPath.<String>read(
                                    wrongPassword.getResponse().getContentAsString(), "$.code"))
                    .isEqualTo("INVALID_CREDENTIALS");
            assertThat(
                            JsonPath.<String>read(
                                    unknownEmail.getResponse().getContentAsString(), "$.message"))
                    .isEqualTo(
                            JsonPath.<String>read(
                                    wrongPassword.getResponse().getContentAsString(), "$.message"));
        }

        @Test
        @DisplayName("同一 IP から5分間に11回目のログインは 429 TOO_MANY_REQUESTS")
        void rateLimited() throws Exception {
            String ip = uniqueIp();
            for (int i = 0; i < 10; i++) {
                assertThat(login("x@example.com", "Wrong0000", ip).getResponse().getStatus())
                        .isEqualTo(401);
            }

            MvcResult blocked = login("x@example.com", "Wrong0000", ip);

            assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
            assertThat(JsonPath.<String>read(blocked.getResponse().getContentAsString(), "$.code"))
                    .isEqualTo("TOO_MANY_REQUESTS");
        }
    }

    @Nested
    @DisplayName("認証ガード・アクセストークン")
    class AccessToken {

        @Test
        @DisplayName("アクセストークンで /users/me を取得できる")
        void meWithToken() throws Exception {
            String token = accessToken(signup("taro@example.com", "taro_trip"));

            mockMvc.perform(
                            get("/api/v1/users/me")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("taro_trip"))
                    .andExpect(jsonPath("$.email").value("taro@example.com"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("トークンなしは 401 UNAUTHORIZED")
        void meWithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("改ざんされたトークンは 401 INVALID_TOKEN")
        void tamperedToken() throws Exception {
            String token = accessToken(signup("taro@example.com", "taro_trip"));

            mockMvc.perform(
                            get("/api/v1/users/me")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("期限切れのトークンは 401 TOKEN_EXPIRED")
        void expiredToken() throws Exception {
            signup("taro@example.com", "taro_trip");
            Instant past = Instant.now().minusSeconds(3600);
            JwtClaimsSet claims =
                    JwtClaimsSet.builder()
                            .issuer("tripdiary-api")
                            .subject("1")
                            .issuedAt(past)
                            .expiresAt(past.plusSeconds(900))
                            .claim("username", "taro_trip")
                            .build();
            String expired =
                    jwtEncoder
                            .encode(
                                    JwtEncoderParameters.from(
                                            JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                            .getTokenValue();

            mockMvc.perform(
                            get("/api/v1/users/me")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("期限切れのトークンが付いていても、認証 API（トークン更新）は妨げられない")
        void authEndpointsIgnoreStaleToken() throws Exception {
            MvcResult signedUp = signup("taro@example.com", "taro_trip");

            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(refreshCookie(signedUp))
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer stale.token.value"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("認証済みリクエストのアクセスログには MDC で userId が付く")
        void accessLogHasUserId() throws Exception {
            String token = accessToken(signup("taro@example.com", "taro_trip"));
            Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                mockMvc.perform(
                        get("/api/v1/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
            } finally {
                logger.detachAppender(appender);
            }

            ILoggingEvent accessLog =
                    appender.list.stream()
                            .filter(e -> e.getFormattedMessage().contains("path=/api/v1/users/me"))
                            .findFirst()
                            .orElseThrow();
            assertThat(accessLog.getMDCPropertyMap()).containsEntry(MdcKeys.USER_ID, "1");
        }
    }

    @Nested
    @DisplayName("トークン更新・ログアウト")
    class RefreshAndLogout {

        @Test
        @DisplayName("トークン更新で新しいアクセストークンとリフレッシュトークンを発行し、古いリフレッシュトークンは使えなくなる")
        void refreshRotates() throws Exception {
            Cookie original = refreshCookie(signup("taro@example.com", "taro_trip"));

            MvcResult refreshed = refresh(original);

            assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
            Cookie rotated = refreshCookie(refreshed);
            assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
            assertThat(accessToken(refreshed)).isNotBlank();
        }

        @Test
        @DisplayName("使用済みのリフレッシュトークンを再利用すると 401 になり、新しいトークンも含めて全て失効する（盗用対策）")
        void reuseRevokesAll() throws Exception {
            Cookie original = refreshCookie(signup("taro@example.com", "taro_trip"));
            Cookie rotated = refreshCookie(refresh(original));

            MvcResult reused = refresh(original);
            assertThat(reused.getResponse().getStatus()).isEqualTo(401);
            assertThat(JsonPath.<String>read(reused.getResponse().getContentAsString(), "$.code"))
                    .isEqualTo("INVALID_REFRESH_TOKEN");

            assertThat(refresh(rotated).getResponse().getStatus()).isEqualTo(401);
            Integer active =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                            Integer.class);
            assertThat(active).isZero();
        }

        @Test
        @DisplayName("Cookie が無い・不明なリフレッシュトークンは 401 INVALID_REFRESH_TOKEN")
        void refreshWithoutCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
            mockMvc.perform(
                            post("/api/v1/auth/refresh")
                                    .cookie(
                                            new Cookie(
                                                    RefreshTokenCookies.NAME,
                                                    UUID.randomUUID().toString())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ログアウトするとリフレッシュトークンが失効し、Cookie が削除される")
        void logout() throws Exception {
            Cookie cookie = refreshCookie(signup("taro@example.com", "taro_trip"));

            MvcResult result =
                    mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                            .andExpect(status().isNoContent())
                            .andReturn();

            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                    .contains("Max-Age=0");
            assertThat(refresh(cookie).getResponse().getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("ログアウトは Cookie が無くても成功する（冪等）")
        void logoutWithoutCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("認証 API が Swagger（OpenAPI）に掲載されている")
    void documentedInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/users/me'].get.security[0].bearerAuth")
                                .exists());
    }
}
