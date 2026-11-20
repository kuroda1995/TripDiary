package com.tripdiary.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** コミュニティ称号の結合テスト（docs/features/11_community_reactions.md 8章）。 */
@IntegrationTest
class CommunityBadgeIntegrationTest {

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

    private long createSpot(TestAccount account) throws Exception {
        String response =
                mockMvc.perform(
                                account.authorize(post("/api/v1/spots"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"title":"清水寺","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC"}
                                                """
                                                        .formatted(kyoto)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long comment(TestAccount account, long spotId) throws Exception {
        String response =
                mockMvc.perform(
                                account.authorize(post("/api/v1/spots/" + spotId + "/comments"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"body\":\"参考になりました\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createTopic(TestAccount account) throws Exception {
        String response =
                mockMvc.perform(
                                account.authorize(post("/api/v1/board-topics"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"title\":\"相談\",\"body\":\"本文\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void reply(TestAccount account, long topicId) throws Exception {
        mockMvc.perform(
                        account.authorize(post("/api/v1/board-topics/" + topicId + "/replies"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"body\":\"返信\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions profile(String username) throws Exception {
        return mockMvc.perform(get("/api/v1/users/" + username));
    }

    @Test
    @DisplayName("書き込みがなければ称号なし、1件で BEGINNER・3件で PILGRIM・5件で MASTER（投稿・コメント・掲示板の合計）")
    void badgeByActivityCount() throws Exception {
        profile("alice")
                .andExpect(jsonPath("$.activityCount").value(0))
                .andExpect(jsonPath("$.badge").value(Matchers.nullValue()));

        long spot = createSpot(alice);
        profile("alice")
                .andExpect(jsonPath("$.activityCount").value(1))
                .andExpect(jsonPath("$.badge").value("BEGINNER"));

        comment(alice, spot);
        long topic = createTopic(alice);
        profile("alice")
                .andExpect(jsonPath("$.activityCount").value(3))
                .andExpect(jsonPath("$.badge").value("PILGRIM"));

        reply(alice, topic);
        reply(alice, topic);
        profile("alice")
                .andExpect(jsonPath("$.activityCount").value(5))
                .andExpect(jsonPath("$.badge").value("MASTER"));

        // 投稿者の情報（スポット詳細・コメント・掲示板）にも称号が付く
        mockMvc.perform(get("/api/v1/spots/" + spot))
                .andExpect(jsonPath("$.author.badge").value("MASTER"));
        mockMvc.perform(get("/api/v1/spots/" + spot + "/comments"))
                .andExpect(jsonPath("$.items[0].author.badge").value("MASTER"));
        mockMvc.perform(get("/api/v1/board-topics"))
                .andExpect(jsonPath("$.items[0].author.badge").value("MASTER"));
    }

    @Test
    @DisplayName("削除すると数が減り、他人の投稿の削除で自分のコメントが消えた場合も減る")
    void decreasesOnDelete() throws Exception {
        long bobSpot = createSpot(bob);
        long aliceComment = comment(alice, bobSpot);
        comment(alice, bobSpot);
        profile("alice").andExpect(jsonPath("$.activityCount").value(2));

        mockMvc.perform(alice.authorize(delete("/api/v1/comments/" + aliceComment)))
                .andExpect(status().isNoContent());
        profile("alice").andExpect(jsonPath("$.activityCount").value(1));

        // bob のスポットを削除すると、付いていた alice のコメントも連鎖削除される
        mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + bobSpot)))
                .andExpect(status().isNoContent());
        profile("alice")
                .andExpect(jsonPath("$.activityCount").value(0))
                .andExpect(jsonPath("$.badge").value(Matchers.nullValue()));
        profile("bob").andExpect(jsonPath("$.activityCount").value(0));
    }

    @Test
    @DisplayName("プロフィールを編集しても活動数は上書きされない（アプリからは書き込まない）")
    void profileUpdateKeepsActivityCount() throws Exception {
        createSpot(alice);

        mockMvc.perform(
                        alice.authorize(patch("/api/v1/users/me"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bio\":\"お寺めぐりが好きです\"}"))
                .andExpect(status().isOk());

        profile("alice").andExpect(jsonPath("$.activityCount").value(1));
    }
}
