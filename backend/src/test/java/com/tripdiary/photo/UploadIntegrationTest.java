package com.tripdiary.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import com.tripdiary.support.PhotoTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 署名付き URL 発行 API の結合テスト（docs/features/03_photo_upload.md 9章）。 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class UploadIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStorage photoStorage;

    private TestAccount alice;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
    }

    private ResultActions issue(String json) throws Exception {
        return mockMvc.perform(
                alice.authorize(post("/api/v1/uploads/presigned-url"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    @Test
    @DisplayName("発行した URL へ PUT すると S3 に保存され、キーは spots/{自分のID}/ 配下になる")
    void issuedUrlAcceptsUpload(CapturedOutput output) throws Exception {
        String response =
                PhotoTestClient.issueUrl(
                        mockMvc,
                        alice,
                        "SPOT_PHOTO",
                        "image/jpeg",
                        PhotoTestClient.JPEG_BYTES.length);
        String key = JsonPath.read(response, "$.s3Key");
        String uploadUrl = JsonPath.read(response, "$.uploadUrl");

        assertThat(key).startsWith("spots/" + alice.id() + "/").endsWith(".jpg");
        assertThat((String) JsonPath.read(response, "$.expiresAt")).isNotBlank();
        assertThat(PhotoTestClient.put(uploadUrl, "image/jpeg", PhotoTestClient.JPEG_BYTES))
                .isEqualTo(200);
        assertThat(photoStorage.head(key))
                .hasValueSatisfying(
                        stored -> {
                            assertThat(stored.contentType()).isEqualTo("image/jpeg");
                            assertThat(stored.size()).isEqualTo(PhotoTestClient.JPEG_BYTES.length);
                        });
        // 署名付き URL（署名部分）はログに出さない
        assertThat(output.getAll()).doesNotContain("X-Amz-Signature").doesNotContain(uploadUrl);
    }

    @Test
    @DisplayName("未ログインでは発行できない（401）")
    void requiresLogin() throws Exception {
        mockMvc.perform(
                        post("/api/v1/uploads/presigned-url")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"purpose":"SPOT_PHOTO","contentType":"image/jpeg","contentLength":100}
                                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("対応外の形式は UNSUPPORTED_CONTENT_TYPE、サイズ超過は FILE_TOO_LARGE")
    void rejectsInvalidFiles() throws Exception {
        issue(
                        """
                        {"purpose":"SPOT_PHOTO","contentType":"image/gif","contentLength":100}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTENT_TYPE"));
        issue(
                        """
                        {"purpose":"SPOT_PHOTO","contentType":"image/jpeg","contentLength":10485761}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        issue(
                        """
                        {"purpose":"AVATAR","contentType":"image/jpeg","contentLength":5242881}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("項目の不足・0 バイト・未知の用途は VALIDATION_ERROR")
    void validation() throws Exception {
        issue(
                        """
                        {"purpose":"SPOT_PHOTO","contentLength":0}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(
                        jsonPath("$.details[*].field")
                                .value(
                                        org.hamcrest.Matchers.containsInAnyOrder(
                                                "contentType", "contentLength")));
        issue(
                        """
                        {"purpose":"BANNER","contentType":"image/jpeg","contentLength":100}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
