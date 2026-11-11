package com.tripdiary.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code spots.like_count} が {@code spot_likes} の件数と常に一致すること（V5 のトリガー。docs/perf_test_report.md 4章
 * #1）。
 */
@IntegrationTest
class LikeCountIntegrationTest {

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

    private long likeCountColumn(long spotId) {
        return jdbc.queryForObject("SELECT like_count FROM spots WHERE id = ?", Long.class, spotId);
    }

    /** すべてのスポットで、列の値と実際の件数が一致していること。 */
    private void assertConsistent() {
        Long mismatches =
                jdbc.queryForObject(
                        "SELECT count(*) FROM spots s WHERE s.like_count <>"
                                + " (SELECT count(*) FROM spot_likes l WHERE l.spot_id = s.id)",
                        Long.class);
        assertThat(mismatches).isZero();
    }

    @Test
    @DisplayName("作成直後は 0。付ける・外すで増減し、二重に付けても・付いていないのに外しても変わらない")
    void followsLikeAndUnlike() throws Exception {
        long spot = createSpot(alice);
        assertThat(likeCountColumn(spot)).isZero();

        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + spot + "/like")))
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + spot + "/like")))
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(alice.authorize(put("/api/v1/spots/" + spot + "/like")))
                .andExpect(jsonPath("$.likeCount").value(2));
        assertThat(likeCountColumn(spot)).isEqualTo(2);

        mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + spot + "/like")))
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(bob.authorize(delete("/api/v1/spots/" + spot + "/like")))
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(likeCountColumn(spot)).isEqualTo(1);
        assertConsistent();
    }

    @Test
    @DisplayName("ユーザーを削除すると、そのユーザーが付けた分が連鎖削除とともに減る")
    void decrementsOnUserDeletion() throws Exception {
        long spot1 = createSpot(alice);
        long spot2 = createSpot(alice);
        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + spot1 + "/like")));
        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + spot2 + "/like")));
        mockMvc.perform(alice.authorize(put("/api/v1/spots/" + spot1 + "/like")));

        jdbc.update("DELETE FROM users WHERE id = ?", bob.id());

        assertThat(likeCountColumn(spot1)).isEqualTo(1);
        assertThat(likeCountColumn(spot2)).isZero();
        assertConsistent();
    }

    @Test
    @DisplayName("行ってみたい！が付いたスポットも削除でき、件数の更新でエラーにならない")
    void spotDeletionWithLikes() throws Exception {
        long spot = createSpot(alice);
        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + spot + "/like")));

        mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spot)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM spot_likes", Long.class)).isZero();
        // 投稿者ごと削除しても同じ
        long other = createSpot(alice);
        mockMvc.perform(bob.authorize(put("/api/v1/spots/" + other + "/like")));
        jdbc.update("DELETE FROM users WHERE id = ?", alice.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spots", Long.class)).isZero();
    }

    @Test
    @DisplayName("同じスポットに大勢が同時に付けても、件数が取りこぼされない")
    void concurrentLikes() throws Exception {
        long spot = createSpot(alice);
        int users = 30;
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            userIds.add(
                    jdbc.queryForObject(
                            "INSERT INTO users (email, username, password_hash, display_name)"
                                    + " VALUES (?, ?, 'x', 'fan') RETURNING id",
                            Long.class,
                            "fan" + i + "@example.com",
                            "fan_" + i));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long userId : userIds) {
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return jdbc.update(
                                    "INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)"
                                            + " ON CONFLICT DO NOTHING",
                                    userId,
                                    spot);
                        };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(likeCountColumn(spot)).isEqualTo(users);
        assertConsistent();
    }

    @Test
    @DisplayName("件数を負の値にはできない（CHECK 制約）")
    void notNegative() throws Exception {
        long spot = createSpot(alice);

        assertThatThrownBy(() -> jdbc.update("UPDATE spots SET like_count = -1 WHERE id = ?", spot))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
