package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** スポットへの写真の紐付け・差し替え・削除の結合テスト（docs/qa_test_plan.md 4.2）。 */
@IntegrationTest
class SpotPhotoIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStorage photoStorage;

    private TestAccount alice;
    private TestAccount bob;
    private long kyoto;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        bob = ApiTestClient.signup(mockMvc, "bob");
        kyoto = jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
    }

    private String body(List<String> keysInOrder) {
        List<String> photos = new ArrayList<>();
        for (int i = 0; i < keysInOrder.size(); i++) {
            photos.add("{\"s3Key\":\"%s\",\"sortOrder\":%d}".formatted(keysInOrder.get(i), i));
        }
        return """
                {"title":"朝の清水寺","locationName":"清水寺","areaTagIds":[%d],"visibility":"PUBLIC","photos":[%s]}
                """
                .formatted(kyoto, String.join(",", photos));
    }

    private ResultActions create(TestAccount account, List<String> keys) throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/spots"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(keys)));
    }

    private long createSpot(TestAccount account, List<String> keys) throws Exception {
        String response =
                create(account, keys)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions update(TestAccount account, long spotId, List<String> keys)
            throws Exception {
        return mockMvc.perform(
                account.authorize(put("/api/v1/spots/" + spotId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(keys)));
    }

    private void awaitDeleted(String key) {
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(photoStorage.head(key)).isEmpty());
    }

    @Test
    @DisplayName("アップロードした写真を付けて投稿すると、詳細に表示順で URL が返り、一覧にはカバー写真が出る")
    void createWithPhotos() throws Exception {
        String first = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String second = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);

        String response =
                create(alice, List.of(first, second))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.photos[*].s3Key", Matchers.contains(first, second)))
                        .andExpect(jsonPath("$.photos[*].sortOrder", Matchers.contains(0, 1)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // 配信 URL から実際に画像を取得できる
        String url = JsonPath.read(response, "$.photos[0].url");
        assertThat(url).endsWith("/" + first);
        assertThat(PhotoTestClient.get(url)).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content_type FROM spot_photos WHERE s3_key = ?",
                                String.class,
                                first))
                .isEqualTo("image/jpeg");

        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(jsonPath("$.items[0].coverPhotoUrl").value(url));
    }

    @Test
    @DisplayName("写真なしの投稿は photos が空、coverPhotoUrl が null")
    void createWithoutPhotos() throws Exception {
        create(alice, List.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos").isEmpty());
        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(jsonPath("$.items[0].coverPhotoUrl").isEmpty());
    }

    @Test
    @DisplayName("他人のキー・存在しないキー・他の投稿で使用済みのキーは INVALID_PHOTO_KEY")
    void rejectsInvalidKeys() throws Exception {
        String bobsKey = PhotoTestClient.uploadSpotPhoto(mockMvc, bob);
        create(alice, List.of(bobsKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_KEY"));

        // 署名付き URL は発行したがアップロードしていない
        String notUploaded =
                JsonPath.read(
                        PhotoTestClient.issueUrl(mockMvc, alice, "SPOT_PHOTO", "image/jpeg", 10),
                        "$.s3Key");
        create(alice, List.of(notUploaded))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_KEY"));

        String used = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        createSpot(alice, List.of(used));
        create(alice, List.of(used))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO_KEY"));

        // 失敗した投稿は保存されない
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spots", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("11枚は TOO_MANY_PHOTOS、並び順の重複は DUPLICATE_PHOTO_ORDER、範囲外は VALIDATION_ERROR")
    void photoCountAndOrder() throws Exception {
        // sortOrder は 0〜9
        mockMvc.perform(
                        alice.authorize(post("/api/v1/spots"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"t","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC",
                                         "photos":[{"s3Key":"a","sortOrder":10}]}
                                        """
                                                .formatted(kyoto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("photos[0].sortOrder"));

        String json =
                """
                {"title":"t","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC",
                 "photos":[{"s3Key":"a","sortOrder":0},{"s3Key":"b","sortOrder":0}]}
                """
                        .formatted(kyoto);
        mockMvc.perform(
                        alice.authorize(post("/api/v1/spots"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PHOTO_ORDER"));

        StringBuilder photos = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            photos.append(i == 0 ? "" : ",")
                    .append("{\"s3Key\":\"k%d\",\"sortOrder\":%d}".formatted(i, i % 10));
        }
        mockMvc.perform(
                        alice.authorize(post("/api/v1/spots"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"t","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC","photos":[%s]}
                                        """
                                                .formatted(kyoto, photos)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PHOTOS"));
    }

    @Test
    @DisplayName("更新で並べ替え・追加・削除ができ、外した写真のファイルは S3 から削除される")
    void updateReplacesPhotos() throws Exception {
        String a = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String b = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String c = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        long spotId = createSpot(alice, List.of(a, b));

        // b を先頭に、a を外して c を追加（並び順の入れ替えも含む）
        update(alice, spotId, List.of(b, c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos[*].s3Key", Matchers.contains(b, c)));

        mockMvc.perform(get("/api/v1/spots/" + spotId))
                .andExpect(jsonPath("$.photos[*].s3Key", Matchers.contains(b, c)))
                .andExpect(jsonPath("$.photos[*].sortOrder", Matchers.contains(0, 1)));
        awaitDeleted(a);
        assertThat(photoStorage.head(b)).isPresent();
        assertThat(photoStorage.head(c)).isPresent();
    }

    @Test
    @DisplayName("更新が失敗したら写真のファイルは消さない")
    void failedUpdateKeepsFiles() throws Exception {
        String a = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        long spotId = createSpot(alice, List.of(a));

        // 他人は更新できない
        update(bob, spotId, List.of()).andExpect(status().isForbidden());
        // 不正なキーを含む更新は全体が失敗する
        update(alice, spotId, List.of("spots/%d/bad.jpg".formatted(alice.id())))
                .andExpect(status().isBadRequest());

        Thread.sleep(500);
        assertThat(photoStorage.head(a)).isPresent();
        mockMvc.perform(get("/api/v1/spots/" + spotId))
                .andExpect(jsonPath("$.photos[*].s3Key", Matchers.contains(a)));
    }

    @Test
    @DisplayName("スポットを削除すると写真の行と S3 のファイルも削除される")
    void deleteRemovesFiles() throws Exception {
        String a = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String b = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        long spotId = createSpot(alice, List.of(a, b));

        mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spotId)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM spot_photos", Long.class)).isZero();
        awaitDeleted(a);
        awaitDeleted(b);
    }
}
