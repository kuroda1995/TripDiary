package com.tripdiary.helpful;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 参考になった API の結合テスト（docs/features/11_community_reactions.md 8章）。 */
@IntegrationTest
class HelpfulIntegrationTest {

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

    private long createSpot(String visibility) throws Exception {
        String response =
                mockMvc.perform(
                                alice.authorize(post("/api/v1/spots"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"title":"清水寺","locationName":"l","areaTagIds":[%d],"visibility":"%s"}
                                                """
                                                        .formatted(kyoto, visibility)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions mark(TestAccount account, long spotId) throws Exception {
        return mockMvc.perform(account.authorize(put("/api/v1/spots/" + spotId + "/helpful")));
    }

    @Test
    @DisplayName("付けると件数が増え、外すと戻る。詳細・一覧の helpfulCount / helpfulByMe も変わり、行ってみたい！とは別に数える")
    void markAndUnmark() throws Exception {
        long spot = createSpot("PUBLIC");

        mark(bob, spot)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spotId").value(spot))
                .andExpect(jsonPath("$.helpful").value(true))
                .andExpect(jsonPath("$.helpfulCount").value(1));
        mark(bob, spot).andExpect(jsonPath("$.helpfulCount").value(1));

        mockMvc.perform(bob.authorize(get("/api/v1/spots/" + spot)))
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.helpfulByMe").value(true))
                .andExpect(jsonPath("$.likeCount").value(0));
        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(jsonPath("$.items[0].helpfulCount").value(1))
                .andExpect(jsonPath("$.items[0].helpfulByMe").value(false));

        mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + spot + "/helpful")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(false))
                .andExpect(jsonPath("$.helpfulCount").value(0));
        // 付いていなくても外せる（冪等）
        mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + spot + "/helpful")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("他人の非公開スポット・存在しないスポットは 404、未ログインは 401")
    void notFoundAndUnauthorized() throws Exception {
        long secret = createSpot("PRIVATE");

        mark(bob, secret)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
        mark(bob, 999_999).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/spots/" + secret + "/helpful"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("スポットを削除すると参考になったも削除される")
    void cascade() throws Exception {
        long spot = createSpot("PUBLIC");
        mark(bob, spot).andExpect(status().isOk());

        mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spot)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM spot_helpfuls", Long.class)).isZero();
    }
}
