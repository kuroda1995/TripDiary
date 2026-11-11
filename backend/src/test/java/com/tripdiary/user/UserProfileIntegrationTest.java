package com.tripdiary.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import com.tripdiary.support.PhotoTestClient;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** プロフィール更新・パスワード変更の結合テスト（docs/qa_test_plan.md 4.1）。 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class UserProfileIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStorage photoStorage;

    private TestAccount alice;

    @BeforeEach
    void setUp() throws Exception {
        // ID を振り直さない: パスワード変更の試行回数はユーザー ID ごとに数えるため、テスト間で同じ ID を使い回さない
        jdbc.execute("TRUNCATE users CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
    }

    private ResultActions patchMe(TestAccount account, String json) throws Exception {
        return mockMvc.perform(
                account.authorize(patch("/api/v1/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private String uploadAvatar(TestAccount account) throws Exception {
        return PhotoTestClient.upload(
                mockMvc, account, "AVATAR", "image/png", PhotoTestClient.JPEG_BYTES);
    }

    private void awaitDeleted(String key) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(photoStorage.head(key)).isEmpty());
    }

    @Nested
    @DisplayName("プロフィール更新")
    class UpdateProfile {

        @Test
        @DisplayName("送った項目だけを更新し、送らなかった項目はそのまま")
        void partialUpdate() throws Exception {
            patchMe(alice, "{\"bio\":\"週末は温泉めぐり♨\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bio").value("週末は温泉めぐり♨"))
                    .andExpect(jsonPath("$.displayName").value("alice さん"));

            patchMe(alice, "{\"displayName\":\"  タロウ  \"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("タロウ"))
                    .andExpect(jsonPath("$.bio").value("週末は温泉めぐり♨"));

            mockMvc.perform(alice.authorize(get("/api/v1/users/me")))
                    .andExpect(jsonPath("$.displayName").value("タロウ"))
                    .andExpect(jsonPath("$.bio").value("週末は温泉めぐり♨"))
                    .andExpect(jsonPath("$.username").value("alice"));
        }

        @Test
        @DisplayName("自己紹介は null で削除できる")
        void clearBio() throws Exception {
            patchMe(alice, "{\"bio\":\"x\"}").andExpect(status().isOk());

            patchMe(alice, "{\"bio\":null}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bio").isEmpty());
        }

        @Test
        @DisplayName("表示名の null・空白・51文字、自己紹介の501文字は VALIDATION_ERROR")
        void validation() throws Exception {
            for (String json :
                    new String[] {
                        "{\"displayName\":null}",
                        "{\"displayName\":\"  \"}",
                        "{\"displayName\":\"" + "あ".repeat(51) + "\"}",
                        "{\"bio\":\"" + "あ".repeat(501) + "\"}"
                    }) {
                patchMe(alice, json)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            }
            patchMe(alice, "{\"displayName\":\"" + "あ".repeat(50) + "\"}")
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("未ログインでは更新できない（401）")
        void requiresLogin() throws Exception {
            mockMvc.perform(
                            patch("/api/v1/users/me")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"bio\":\"x\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("アイコン")
    class Avatar {

        @Test
        @DisplayName("アップロードしたアイコンを設定すると URL が返り、スポットの投稿者にも表示される")
        void setAvatar() throws Exception {
            String key = uploadAvatar(alice);

            String response =
                    patchMe(alice, "{\"avatarS3Key\":\"%s\"}".formatted(key))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            String url = JsonPath.read(response, "$.avatarUrl");
            assertThat(url).endsWith("/" + key);
            assertThat(PhotoTestClient.get(url)).isEqualTo(200);

            long kyoto =
                    jdbc.queryForObject(
                            "SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
            mockMvc.perform(
                            alice.authorize(post("/api/v1/spots"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"title":"t","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC"}
                                            """
                                                    .formatted(kyoto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.author.avatarUrl").value(url));
            mockMvc.perform(get("/api/v1/spots"))
                    .andExpect(jsonPath("$.items[0].author.avatarUrl").value(url));
        }

        @Test
        @DisplayName("差し替え・削除すると古いファイルは S3 から削除される")
        void replaceAndRemove() throws Exception {
            String first = uploadAvatar(alice);
            String second = uploadAvatar(alice);
            patchMe(alice, "{\"avatarS3Key\":\"%s\"}".formatted(first)).andExpect(status().isOk());

            patchMe(alice, "{\"avatarS3Key\":\"%s\"}".formatted(second)).andExpect(status().isOk());
            awaitDeleted(first);
            assertThat(photoStorage.head(second)).isPresent();

            patchMe(alice, "{\"avatarS3Key\":null}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").isEmpty());
            awaitDeleted(second);
        }

        @Test
        @DisplayName("他人のアイコン・スポット写真・アップロードしていないキーは INVALID_AVATAR_KEY")
        void rejectsInvalidKeys() throws Exception {
            TestAccount bob = ApiTestClient.signup(mockMvc, "bob");
            String bobsAvatar = uploadAvatar(bob);
            String spotPhoto = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
            String notUploaded =
                    JsonPath.read(
                            PhotoTestClient.issueUrl(mockMvc, alice, "AVATAR", "image/png", 10),
                            "$.s3Key");

            for (String key : new String[] {bobsAvatar, spotPhoto, notUploaded}) {
                patchMe(alice, "{\"avatarS3Key\":\"%s\"}".formatted(key))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_AVATAR_KEY"));
            }
        }
    }

    @Nested
    @DisplayName("パスワード変更")
    class ChangePassword {

        private MvcResult signupWithCookie(String username) throws Exception {
            return mockMvc.perform(
                            post("/api/v1/auth/signup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email":"%s@example.com","username":"%s","displayName":"x","password":"Passw0rd123"}
                                            """
                                                    .formatted(username, username)))
                    .andExpect(status().isCreated())
                    .andReturn();
        }

        private ResultActions change(String accessToken, String current, String next)
                throws Exception {
            return mockMvc.perform(
                    put("/api/v1/users/me/password")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {"currentPassword":"%s","newPassword":"%s"}
                                    """
                                            .formatted(current, next)));
        }

        private ResultActions login(String email, String password) throws Exception {
            return mockMvc.perform(
                    post("/api/v1/auth/login")
                            .with(
                                    r -> {
                                        r.setRemoteAddr("10.9.9." + (int) (Math.random() * 250));
                                        return r;
                                    })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    """
                                    {"email":"%s","password":"%s"}
                                    """
                                            .formatted(email, password)));
        }

        private ResultActions refresh(Cookie cookie) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie));
        }

        @Test
        @DisplayName("変更すると新しいトークンが返り、新しいパスワードでだけログインでき、他の端末のリフレッシュトークンは失効する")
        void changePassword() throws Exception {
            MvcResult signedUp = signupWithCookie("taro");
            String accessToken =
                    JsonPath.read(signedUp.getResponse().getContentAsString(), "$.accessToken");
            Cookie otherDevice = signedUp.getResponse().getCookie("refresh_token");

            MvcResult changed =
                    change(accessToken, "Passw0rd123", "NewPass456")
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.accessToken").isNotEmpty())
                            .andExpect(jsonPath("$.user.username").value("taro"))
                            .andReturn();
            Cookie thisDevice = changed.getResponse().getCookie("refresh_token");
            assertThat(thisDevice).isNotNull();
            assertThat(thisDevice.isHttpOnly()).isTrue();

            // この端末は新しいリフレッシュトークンでログインを続けられる
            refresh(thisDevice).andExpect(status().isOk());
            // 変更前のリフレッシュトークン（他の端末）は使えない
            refresh(otherDevice)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

            login("taro@example.com", "Passw0rd123").andExpect(status().isUnauthorized());
            login("taro@example.com", "NewPass456").andExpect(status().isOk());
        }

        @Test
        @DisplayName(
                "現在のパスワード違いは INVALID_CURRENT_PASSWORD、同じパスワードは PASSWORD_UNCHANGED、弱いパスワードは VALIDATION_ERROR")
        void errors() throws Exception {
            change(alice.accessToken(), "Wrong1234", "NewPass456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
            change(alice.accessToken(), "Passw0rd123", "Passw0rd123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
            change(alice.accessToken(), "Passw0rd123", "short")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("newPassword"));
        }

        @Test
        @DisplayName("5分間に10回を超えると 429（現在のパスワードの総当たり対策）")
        void rateLimited() throws Exception {
            for (int i = 0; i < 10; i++) {
                change(alice.accessToken(), "Wrong1234", "NewPass456")
                        .andExpect(status().isBadRequest());
            }
            change(alice.accessToken(), "Passw0rd123", "NewPass456")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        }

        @Test
        @DisplayName("パスワードはログに出さない")
        void passwordsAreNotLogged(CapturedOutput output) throws Exception {
            change(alice.accessToken(), "Passw0rd123", "Secret98765").andExpect(status().isOk());

            assertThat(output.getAll()).doesNotContain("Secret98765").doesNotContain("Passw0rd123");
        }
    }
}
