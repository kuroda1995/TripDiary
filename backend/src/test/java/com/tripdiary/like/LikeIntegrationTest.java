package com.tripdiary.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import java.util.ArrayList;
import java.util.List;
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

/** 行ってみたい！API の結合テスト（docs/qa_test_plan.md 4.3）。 */
@IntegrationTest
class LikeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

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

    private long createSpot(TestAccount account, String title, String visibility) throws Exception {
        String response =
                mockMvc.perform(
                                account.authorize(post("/api/v1/spots"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"title":"%s","locationName":"l","areaTagIds":[%d],"visibility":"%s"}
                                                """
                                                        .formatted(title, kyoto, visibility)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions like(TestAccount account, long spotId) throws Exception {
        return mockMvc.perform(account.authorize(put("/api/v1/spots/" + spotId + "/like")));
    }

    private ResultActions unlike(TestAccount account, long spotId) throws Exception {
        return mockMvc.perform(account.authorize(delete("/api/v1/spots/" + spotId + "/like")));
    }

    @Nested
    @DisplayName("付ける・外す")
    class Toggle {

        @Test
        @DisplayName("付けると liked=true・件数が増え、外すと元に戻る。詳細の likeCount / likedByMe も変わる")
        void likeAndUnlike() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");

            like(bob, spot)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.spotId").value(spot))
                    .andExpect(jsonPath("$.liked").value(true))
                    .andExpect(jsonPath("$.likeCount").value(1));
            like(alice, spot).andExpect(jsonPath("$.likeCount").value(2));

            mockMvc.perform(bob.authorize(get("/api/v1/spots/" + spot)))
                    .andExpect(jsonPath("$.likeCount").value(2))
                    .andExpect(jsonPath("$.likedByMe").value(true));

            unlike(bob, spot)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.liked").value(false))
                    .andExpect(jsonPath("$.likeCount").value(1));
            mockMvc.perform(bob.authorize(get("/api/v1/spots/" + spot)))
                    .andExpect(jsonPath("$.likedByMe").value(false));
            // 未ログインでも件数は見える
            mockMvc.perform(get("/api/v1/spots/" + spot))
                    .andExpect(jsonPath("$.likeCount").value(1))
                    .andExpect(jsonPath("$.likedByMe").value(false));
        }

        @Test
        @DisplayName("連続で付けても件数は1しか増えず、付いていないのに外しても成功する（冪等）")
        void idempotent() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");

            like(bob, spot).andExpect(jsonPath("$.likeCount").value(1));
            like(bob, spot).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1));
            unlike(alice, spot)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM spot_likes", Long.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("他人の非公開スポット・存在しないスポットは 404 SPOT_NOT_FOUND、本人の非公開スポットには付けられる")
        void privateAndMissing() throws Exception {
            long secret = createSpot(alice, "秘密の場所", "PRIVATE");

            like(bob, secret)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
            unlike(bob, secret).andExpect(status().isNotFound());
            like(bob, 999_999).andExpect(status().isNotFound());
            like(alice, secret).andExpect(status().isOk());
        }

        @Test
        @DisplayName("未ログインでは付けられない（401）")
        void requiresLogin() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");

            mockMvc.perform(put("/api/v1/spots/" + spot + "/like"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/me/likes")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("スポットを削除すると行ってみたい！も削除される")
        void cascadeOnSpotDelete() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");
            like(bob, spot).andExpect(status().isOk());

            mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spot)))
                    .andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM spot_likes", Long.class)).isZero();
        }
    }

    @Test
    @DisplayName("一覧（新着・ユーザー別）にも件数と自分が付けたかが入る")
    void listIncludesReactions() throws Exception {
        long liked = createSpot(alice, "付けた", "PUBLIC");
        createSpot(alice, "付けていない", "PUBLIC");
        like(bob, liked);
        like(alice, liked);

        mockMvc.perform(bob.authorize(get("/api/v1/spots")))
                .andExpect(jsonPath("$.items[?(@.title=='付けた')].likeCount").value(2))
                .andExpect(jsonPath("$.items[?(@.title=='付けた')].likedByMe").value(true))
                .andExpect(jsonPath("$.items[?(@.title=='付けていない')].likeCount").value(0))
                .andExpect(jsonPath("$.items[?(@.title=='付けていない')].likedByMe").value(false));
        mockMvc.perform(get("/api/v1/users/alice/spots"))
                .andExpect(jsonPath("$.items[?(@.title=='付けた')].likeCount").value(2))
                .andExpect(jsonPath("$.items[?(@.title=='付けた')].likedByMe").value(false));
    }

    @Nested
    @DisplayName("行ってみたい！した投稿")
    class MyLikes {

        @Test
        @DisplayName("付けた日時の新しい順に返し、カーソルで続きを取得できる")
        void orderedAndPaged() throws Exception {
            List<Long> spots = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                spots.add(createSpot(alice, "spot" + i, "PUBLIC"));
            }
            // 投稿の順とは逆の順に付ける（投稿日時ではなく付けた日時で並ぶことを確かめる）
            like(bob, spots.get(2));
            like(bob, spots.get(0));
            like(bob, spots.get(1));

            String first =
                    mockMvc.perform(bob.authorize(get("/api/v1/me/likes")).param("limit", "2"))
                            .andExpect(status().isOk())
                            .andExpect(
                                    jsonPath(
                                            "$.items[*].title",
                                            Matchers.contains("spot1", "spot0")))
                            .andExpect(jsonPath("$.items[0].likedByMe").value(true))
                            .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            bob.authorize(get("/api/v1/me/likes"))
                                    .param("limit", "2")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("spot2")))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("外した投稿・あとから非公開になった他人の投稿は含めない")
        void excludesUnlikedAndPrivate() throws Exception {
            long visible = createSpot(alice, "見える", "PUBLIC");
            long unliked = createSpot(alice, "外した", "PUBLIC");
            long becamePrivate = createSpot(alice, "非公開になった", "PUBLIC");
            like(bob, visible);
            like(bob, unliked);
            like(bob, becamePrivate);
            unlike(bob, unliked);
            jdbc.update("UPDATE spots SET visibility = 'PRIVATE' WHERE id = ?", becamePrivate);

            mockMvc.perform(bob.authorize(get("/api/v1/me/likes")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("見える")));
        }

        @Test
        @DisplayName("まだ付けていなければ空、不正なカーソルは 400 INVALID_CURSOR")
        void emptyAndInvalidCursor() throws Exception {
            mockMvc.perform(bob.authorize(get("/api/v1/me/likes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
            mockMvc.perform(bob.authorize(get("/api/v1/me/likes")).param("cursor", "%%%"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
    }
}
