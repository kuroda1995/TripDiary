package com.tripdiary.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** ゲストログインの結合テスト（docs/features/01_auth.md 4.8）。 */
@IntegrationTest
@TestPropertySource(
        properties = {"app.guest.email=guest@example.com", "app.guest.password=Guest1234"})
class GuestLoginIntegrationTest {

    /** ログイン試行制限に引っかからないよう、テストごとに異なるクライアント IP を使う。 */
    private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }

    private void signup(String email, String username, String password) throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"%s","username":"%s","displayName":"ゲスト","password":"%s"}
                                        """
                                                .formatted(email, username, password)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String password) throws Exception {
        String ip = "10.9.0." + IP_SEQ.getAndIncrement();
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"%s","password":"%s"}
                                """
                                        .formatted(email, password))
                        .with(
                                req -> {
                                    req.setRemoteAddr(ip);
                                    return req;
                                }));
    }

    private String accessToken(String email, String password) throws Exception {
        String body = login(email, password).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    @DisplayName("ゲストのユーザーが未登録なら、ログイン情報は GUEST_LOGIN_DISABLED（404）")
    void disabledUntilGuestUserExists() throws Exception {
        mockMvc.perform(get("/api/v1/auth/guest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUEST_LOGIN_DISABLED"));
    }

    @Test
    @DisplayName("ゲストのログイン情報は未ログインでも取得でき、その情報でログインすると guest: true になる")
    void guestCanLogInWithPublishedCredentials() throws Exception {
        signup("guest@example.com", "guest_user", "Guest1234");

        mockMvc.perform(get("/api/v1/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.email").value("guest@example.com"))
                .andExpect(jsonPath("$.password").value("Guest1234"));

        login("guest@example.com", "Guest1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.guest").value(true));
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken("guest@example.com", "Guest1234")))
                .andExpect(jsonPath("$.guest").value(true));
    }

    @Test
    @DisplayName("ゲストはパスワードを変更できず（GUEST_ACCOUNT_RESTRICTED・403）、元のパスワードでログインできる")
    void guestCannotChangePassword() throws Exception {
        signup("guest@example.com", "guest_user", "Guest1234");
        String token = accessToken("guest@example.com", "Guest1234");

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"currentPassword":"Guest1234","newPassword":"Changed999"}
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUEST_ACCOUNT_RESTRICTED"));

        login("guest@example.com", "Guest1234").andExpect(status().isOk());
        login("guest@example.com", "Changed999").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ゲスト以外のユーザーは guest: false で、パスワードを変更できる")
    void otherUsersAreNotRestricted() throws Exception {
        signup("guest@example.com", "guest_user", "Guest1234");
        signup("taro@example.com", "taro_trip", "Passw0rd123");

        login("taro@example.com", "Passw0rd123").andExpect(jsonPath("$.user.guest").value(false));
        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken("taro@example.com", "Passw0rd123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"currentPassword":"Passw0rd123","newPassword":"Changed999"}
                                        """))
                .andExpect(status().isOk());
    }
}
