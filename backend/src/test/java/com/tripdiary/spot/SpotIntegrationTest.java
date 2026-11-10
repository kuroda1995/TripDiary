package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** スポット API の結合テスト（docs/qa_test_plan.md 4.2）。 */
@IntegrationTest
class SpotIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice;
    private TestAccount bob;
    private long kyoto;
    private long osaka;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        bob = ApiTestClient.signup(mockMvc, "bob");
        kyoto = areaId("kyoto");
        osaka = areaId("osaka");
    }

    private long areaId(String slug) {
        return jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = ?", Long.class, slug);
    }

    private static String body(String title, String visibility, long... areaTagIds) {
        String ids =
                String.join(
                        ",",
                        java.util.Arrays.stream(areaTagIds).mapToObj(String::valueOf).toList());
        return """
                {"title":"%s","locationName":"清水寺","areaTagIds":[%s],
                 "impression":"開門直後に行くと人が少なくて最高でした。","visitedOn":"2026-10-03","visibility":"%s"}
                """
                .formatted(title, ids, visibility);
    }

    private ResultActions create(TestAccount account, String json) throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/spots"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    private long createSpot(TestAccount account, String title, String visibility) throws Exception {
        String response =
                create(account, body(title, visibility, kyoto))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Nested
    @DisplayName("作成")
    class Create {

        @Test
        @DisplayName("作成すると 201 と Location ヘッダ、エリアタグ・投稿者を含む詳細を返す")
        void createSucceeds() throws Exception {
            create(alice, body("朝の清水寺", "PUBLIC", osaka, kyoto))
                    .andExpect(status().isCreated())
                    .andExpect(
                            header().string(
                                            "Location",
                                            Matchers.matchesPattern("/api/v1/spots/\\d+")))
                    .andExpect(jsonPath("$.title").value("朝の清水寺"))
                    .andExpect(jsonPath("$.visitedOn").value("2026-10-03"))
                    .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                    .andExpect(jsonPath("$.author.username").value("alice"))
                    // エリアタグは表示順（京都 → 大阪）
                    .andExpect(jsonPath("$.areaTags[*].slug", Matchers.contains("kyoto", "osaka")));
        }

        @Test
        @DisplayName("未ログインでは作成できない（401）")
        void requiresLogin() throws Exception {
            mockMvc.perform(
                            post("/api/v1/spots")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body("x", "PUBLIC", kyoto)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("必須項目・文字数・エリアタグ数（1〜5）の違反は 400 VALIDATION_ERROR")
        void validation() throws Exception {
            create(
                            alice,
                            """
                            {"title":" ","locationName":"","areaTagIds":[],"visibility":"PUBLIC","impression":"%s"}
                            """
                                    .formatted("あ".repeat(5001)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(
                            jsonPath(
                                    "$.details[*].field",
                                    Matchers.hasItems(
                                            "title", "locationName", "areaTagIds", "impression")));

            create(alice, body("x", "PUBLIC", 1, 2, 3, 4, 5, 6))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("areaTagIds"));
        }

        @Test
        @DisplayName("存在しないエリアタグ・重複したエリアタグ・不正な公開範囲は 400")
        void invalidAreaTagsAndVisibility() throws Exception {
            create(alice, body("x", "PUBLIC", 999999))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AREA_TAG_NOT_FOUND"));
            create(alice, body("x", "PUBLIC", kyoto, kyoto))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_AREA_TAG"));
            create(alice, body("x", "FRIENDS", kyoto))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("未来の訪問日は 400 VISITED_ON_IN_FUTURE")
        void futureVisitedOn() throws Exception {
            create(alice, body("x", "PUBLIC", kyoto).replace("2026-10-03", "2999-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VISITED_ON_IN_FUTURE"));
        }
    }

    @Nested
    @DisplayName("詳細・公開範囲")
    class Detail {

        @Test
        @DisplayName("公開スポットは未ログインでも閲覧できる")
        void publicSpotIsVisibleToGuests() throws Exception {
            long id = createSpot(alice, "公開スポット", "PUBLIC");

            mockMvc.perform(get("/api/v1/spots/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("公開スポット"))
                    .andExpect(jsonPath("$.impression").value("開門直後に行くと人が少なくて最高でした。"));
        }

        @Test
        @DisplayName("非公開スポットは本人だけが閲覧でき、他人・未ログインには 404")
        void privateSpotIsVisibleOnlyToOwner() throws Exception {
            long id = createSpot(alice, "非公開メモ", "PRIVATE");

            mockMvc.perform(alice.authorize(get("/api/v1/spots/" + id))).andExpect(status().isOk());
            mockMvc.perform(bob.authorize(get("/api/v1/spots/" + id)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
            mockMvc.perform(get("/api/v1/spots/" + id)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("存在しない ID は 404 SPOT_NOT_FOUND")
        void notFound() throws Exception {
            mockMvc.perform(get("/api/v1/spots/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("更新・削除")
    class UpdateAndDelete {

        @Test
        @DisplayName("本人は全項目を置き換えて更新できる（エリアタグも置き換わる）")
        void ownerCanUpdate() throws Exception {
            long id = createSpot(alice, "変更前", "PUBLIC");

            mockMvc.perform(
                            alice.authorize(put("/api/v1/spots/" + id))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            body("変更後", "PRIVATE", osaka)
                                                    .replace(",\"visitedOn\":\"2026-10-03\"", "")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("変更後"))
                    .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.areaTags[*].slug", Matchers.contains("osaka")));

            Integer tagRows =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM spot_area_tags WHERE spot_id = ?",
                            Integer.class,
                            id);
            assertThat(tagRows).isEqualTo(1);
        }

        @Test
        @DisplayName("他人の公開スポットの更新・削除は 403、他人の非公開スポットは 404")
        void othersCannotEditOrDelete() throws Exception {
            long publicSpot = createSpot(alice, "公開", "PUBLIC");
            long privateSpot = createSpot(alice, "非公開", "PRIVATE");

            mockMvc.perform(
                            bob.authorize(put("/api/v1/spots/" + publicSpot))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body("乗っ取り", "PUBLIC", kyoto)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + publicSpot)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + privateSpot)))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/v1/spots/" + publicSpot))
                    .andExpect(jsonPath("$.title").value("公開"));
        }

        @Test
        @DisplayName("削除するとエリアタグ・いいね・コメント・ステータスも削除される")
        void deleteCascades() throws Exception {
            long id = createSpot(alice, "削除対象", "PUBLIC");
            jdbc.update("INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)", bob.id(), id);
            jdbc.update(
                    "INSERT INTO comments (spot_id, user_id, body) VALUES (?, ?, 'いいですね')",
                    id,
                    bob.id());
            jdbc.update(
                    "INSERT INTO user_spot_statuses (user_id, spot_id, status) VALUES (?, ?, 'WANT_TO_GO')",
                    bob.id(),
                    id);

            mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + id)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/spots/" + id)).andExpect(status().isNotFound());
            Integer remaining =
                    jdbc.queryForObject(
                            "SELECT (SELECT count(*) FROM spot_area_tags) + (SELECT count(*) FROM spot_likes)"
                                    + " + (SELECT count(*) FROM comments) + (SELECT count(*) FROM user_spot_statuses)",
                            Integer.class);
            assertThat(remaining).isZero();
        }
    }

    @Nested
    @DisplayName("一覧")
    class Lists {

        @Test
        @DisplayName("新着一覧は公開スポットのみを新着順で返す")
        void publicListShowsOnlyPublicSpotsNewestFirst() throws Exception {
            createSpot(alice, "1つ目", "PUBLIC");
            createSpot(alice, "非公開", "PRIVATE");
            createSpot(bob, "2つ目", "PUBLIC");

            mockMvc.perform(alice.authorize(get("/api/v1/spots")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("2つ目", "1つ目")))
                    .andExpect(jsonPath("$.items[0].impressionExcerpt").exists())
                    .andExpect(jsonPath("$.items[0].impression").doesNotExist())
                    .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
        }

        @Test
        @DisplayName("カーソルで全ページをたどると、重複・欠落なく全件を取得できる")
        void cursorPagingHasNoDuplicatesOrGaps() throws Exception {
            // 同じ投稿日時のスポットがあっても ID で順序が決まることを確認する
            for (int i = 0; i < 7; i++) {
                createSpot(i % 2 == 0 ? alice : bob, "spot-" + i, "PUBLIC");
            }
            jdbc.update(
                    "UPDATE spots SET created_at = '2026-11-01T00:00:00Z' WHERE title IN ('spot-2', 'spot-3', 'spot-4')");

            List<String> titles = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                var request = get("/api/v1/spots").param("limit", "3");
                if (cursor != null) {
                    request.param("cursor", cursor);
                }
                String response =
                        mockMvc.perform(request)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                titles.addAll(JsonPath.read(response, "$.items[*].title"));
                cursor = JsonPath.read(response, "$.nextCursor");
                pages++;
            } while (cursor != null && pages < 10);

            assertThat(pages).isEqualTo(3);
            assertThat(titles).hasSize(7);
            Set<String> unique = new HashSet<>(titles);
            assertThat(unique).hasSize(7);
        }

        @Test
        @DisplayName("limit は1〜50、不正なカーソルは 400")
        void invalidPagingParameters() throws Exception {
            mockMvc.perform(get("/api/v1/spots").param("limit", "51"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(get("/api/v1/spots").param("limit", "0"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/v1/spots").param("cursor", "broken!"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }

        @Test
        @DisplayName("ユーザー別一覧: 本人には非公開も含め、他人・未ログインには公開のみ")
        void userListRespectsVisibility() throws Exception {
            createSpot(alice, "公開", "PUBLIC");
            createSpot(alice, "非公開", "PRIVATE");
            createSpot(bob, "ボブの投稿", "PUBLIC");

            mockMvc.perform(alice.authorize(get("/api/v1/users/alice/spots")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("非公開", "公開")));
            mockMvc.perform(bob.authorize(get("/api/v1/users/ALICE/spots")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("公開")));
            mockMvc.perform(get("/api/v1/users/alice/spots"))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("公開")));
        }

        @Test
        @DisplayName("存在しないユーザーの一覧は 404 USER_NOT_FOUND")
        void unknownUser() throws Exception {
            mockMvc.perform(get("/api/v1/users/nobody/spots"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("スポット API が Swagger（OpenAPI）に掲載されている")
    void documentedInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/spots'].get").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/spots'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/spots/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/areas'].get").exists());
    }
}
