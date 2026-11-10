package com.tripdiary.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.support.ApiTestClient.TestAccount;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 結合テスト用: ブラウザと同じ手順（署名付き URL の発行 → S3 へ PUT）で写真をアップロードする。 */
public final class PhotoTestClient {

    /** JPEG のファイル先頭（中身の検証はしないため、それらしいバイト列で十分）。 */
    public static final byte[] JPEG_BYTES = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0
    };

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private PhotoTestClient() {}

    /** 署名付き URL を発行する（レスポンスの JSON 文字列を返す）。 */
    public static String issueUrl(
            MockMvc mockMvc, TestAccount account, String purpose, String contentType, long size)
            throws Exception {
        return mockMvc.perform(
                        account.authorize(post("/api/v1/uploads/presigned-url"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"purpose":"%s","contentType":"%s","contentLength":%d}
                                        """
                                                .formatted(purpose, contentType, size)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** スポット写真をアップロードし、S3 キーを返す。 */
    public static String uploadSpotPhoto(MockMvc mockMvc, TestAccount account) throws Exception {
        return upload(mockMvc, account, "SPOT_PHOTO", "image/jpeg", JPEG_BYTES);
    }

    public static String upload(
            MockMvc mockMvc, TestAccount account, String purpose, String contentType, byte[] body)
            throws Exception {
        String response = issueUrl(mockMvc, account, purpose, contentType, body.length);
        int status = put(JsonPath.read(response, "$.uploadUrl"), contentType, body);
        if (status != 200) {
            throw new IllegalStateException("upload failed: HTTP " + status);
        }
        return JsonPath.read(response, "$.s3Key");
    }

    /** 署名付き URL へ PUT する（HTTP ステータスを返す）。 */
    public static int put(String uploadUrl, String contentType, byte[] body) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(uploadUrl))
                        .header("Content-Type", contentType)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** 配信 URL から取得する（HTTP ステータスを返す）。 */
    public static int get(String url) throws Exception {
        return HTTP.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
