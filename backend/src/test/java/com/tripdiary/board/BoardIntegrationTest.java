package com.tripdiary.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
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

/** お寺コミュニティ広場（掲示板）API の結合テスト（docs/features/10_community_board.md 8章）。 */
@IntegrationTest
class BoardIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice;
    private TestAccount bob;
    private TestAccount carol;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        bob = ApiTestClient.signup(mockMvc, "bob");
        carol = ApiTestClient.signup(mockMvc, "carol");
    }

    private ResultActions postTopic(TestAccount account, String title, String body)
            throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/board-topics"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"title":"%s","body":"%s"}
                                """
                                        .formatted(title, body)));
    }

    private long createTopic(TestAccount account, String title) throws Exception {
        String response =
                postTopic(account, title, "本文です")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions postReply(TestAccount account, long topicId, String body)
            throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/board-topics/" + topicId + "/replies"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"%s\"}".formatted(body)));
    }

    private long createReply(TestAccount account, long topicId, String body) throws Exception {
        String response =
                postReply(account, topicId, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Nested
    @DisplayName("トピック")
    class Topics {

        @Test
        @DisplayName("作成すると 201・Location を返し、前後の空白を除いて保存する。未ログインでも詳細を見られる")
        void create() throws Exception {
            String response =
                    postTopic(alice, "  おすすめのお寺相談  ", "  紅葉の時期に行くならどこ？  ")
                            .andExpect(status().isCreated())
                            .andExpect(
                                    header().string(
                                                    "Location",
                                                    Matchers.matchesRegex(
                                                            ".*/api/v1/board-topics/\\d+")))
                            .andExpect(jsonPath("$.title").value("おすすめのお寺相談"))
                            .andExpect(jsonPath("$.body").value("紅葉の時期に行くならどこ？"))
                            .andExpect(jsonPath("$.author.username").value("alice"))
                            .andExpect(jsonPath("$.replyCount").value(0))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            long id = ((Number) JsonPath.read(response, "$.id")).longValue();

            mockMvc.perform(get("/api/v1/board-topics/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("おすすめのお寺相談"));
        }

        @Test
        @DisplayName("一覧は最後に書き込みがあった順で、返信するとそのトピックが先頭に上がる。返信数も返す")
        void orderedByLastActivity() throws Exception {
            long older = createTopic(alice, "御朱印情報交換");
            createTopic(bob, "おすすめのお寺相談");

            mockMvc.perform(get("/api/v1/board-topics"))
                    .andExpect(
                            jsonPath(
                                    "$.items[*].title", Matchers.contains("おすすめのお寺相談", "御朱印情報交換")));

            createReply(carol, older, "瑞龍寺の御朱印は書き置きでした");
            createReply(bob, older, "情報ありがとうございます");

            mockMvc.perform(get("/api/v1/board-topics"))
                    .andExpect(
                            jsonPath("$.items[*].title", Matchers.contains("御朱印情報交換", "おすすめのお寺相談")))
                    .andExpect(jsonPath("$.items[0].replyCount").value(2))
                    .andExpect(jsonPath("$.items[1].replyCount").value(0))
                    .andExpect(jsonPath("$.items[0].bodyExcerpt").value("本文です"));
            mockMvc.perform(get("/api/v1/board-topics/" + older))
                    .andExpect(jsonPath("$.replyCount").value(2));
        }

        @Test
        @DisplayName("カーソルで続きを取得でき、不正なカーソルは 400 INVALID_CURSOR")
        void paging() throws Exception {
            for (int i = 0; i < 3; i++) {
                createTopic(alice, "topic" + i);
            }
            String first =
                    mockMvc.perform(get("/api/v1/board-topics").param("limit", "2"))
                            .andExpect(
                                    jsonPath(
                                            "$.items[*].title",
                                            Matchers.contains("topic2", "topic1")))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            get("/api/v1/board-topics")
                                    .param("limit", "2")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("topic0")))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
            mockMvc.perform(get("/api/v1/board-topics").param("cursor", "%%%"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }

        @Test
        @DisplayName("タイトル・本文が空や長すぎるときは 400 VALIDATION_ERROR")
        void validation() throws Exception {
            postTopic(alice, " ", "本文")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("title"));
            postTopic(alice, "タイトル", "あ".repeat(2001))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("body"));
        }

        @Test
        @DisplayName("未ログインでは作成できない（401）が、一覧は見られる")
        void requiresLoginToPost() throws Exception {
            mockMvc.perform(
                            post("/api/v1/board-topics")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"title\":\"t\",\"body\":\"b\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/board-topics")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないトピックは 404 BOARD_TOPIC_NOT_FOUND")
        void notFound() throws Exception {
            mockMvc.perform(get("/api/v1/board-topics/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BOARD_TOPIC_NOT_FOUND"));
            mockMvc.perform(get("/api/v1/board-topics/999999/replies"))
                    .andExpect(status().isNotFound());
            postReply(bob, 999_999, "返信").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("削除は投稿者本人のみ（他人は 403）。削除すると返信も消える")
        void delete_onlyAuthor() throws Exception {
            long topic = createTopic(alice, "おすすめのお寺相談");
            createReply(bob, topic, "返信");

            mockMvc.perform(bob.authorize(delete("/api/v1/board-topics/" + topic)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(alice.authorize(delete("/api/v1/board-topics/" + topic)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/board-topics/" + topic)).andExpect(status().isNotFound());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM board_replies", Long.class))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("返信")
    class Replies {

        private long topic;

        @BeforeEach
        void setUp() throws Exception {
            topic = createTopic(alice, "御朱印情報交換");
        }

        @Test
        @DisplayName("古い順に返し、カーソルで続きを取得できる。未ログインでも見られる")
        void listOldestFirst() throws Exception {
            createReply(bob, topic, "1つ目");
            createReply(carol, topic, "2つ目");
            createReply(bob, topic, "3つ目");

            String first =
                    mockMvc.perform(
                                    get("/api/v1/board-topics/" + topic + "/replies")
                                            .param("limit", "2"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.items[*].body", Matchers.contains("1つ目", "2つ目")))
                            .andExpect(jsonPath("$.items[0].author.username").value("bob"))
                            .andExpect(jsonPath("$.items[0].topicId").value(topic))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            get("/api/v1/board-topics/" + topic + "/replies")
                                    .param("limit", "2")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].body", Matchers.contains("3つ目")))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("空・1000文字を超える返信は 400、未ログインは 401")
        void validation() throws Exception {
            postReply(bob, topic, "  ")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            postReply(bob, topic, "あ".repeat(1001)).andExpect(status().isBadRequest());
            mockMvc.perform(
                            post("/api/v1/board-topics/" + topic + "/replies")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"body\":\"b\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("返信の投稿者本人とトピックの投稿者は削除でき、それ以外は 403")
        void deletePermissions() throws Exception {
            long byBob = createReply(bob, topic, "bob の返信");
            long byCarol = createReply(carol, topic, "carol の返信");

            mockMvc.perform(carol.authorize(delete("/api/v1/board-replies/" + byBob)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(bob.authorize(delete("/api/v1/board-replies/" + byBob)))
                    .andExpect(status().isNoContent());
            // トピックの投稿者（alice）は他人の返信も削除できる
            mockMvc.perform(alice.authorize(delete("/api/v1/board-replies/" + byCarol)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/board-topics/" + topic + "/replies"))
                    .andExpect(jsonPath("$.items").isEmpty());
            mockMvc.perform(alice.authorize(delete("/api/v1/board-replies/" + byCarol)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BOARD_REPLY_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("ユーザーを削除すると、そのユーザーのトピック・返信も削除される")
    void cascadeOnUserDelete() throws Exception {
        long topic = createTopic(alice, "おすすめのお寺相談");
        createReply(bob, topic, "返信");

        jdbc.update("DELETE FROM users WHERE id = ?", bob.id());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_replies", Long.class)).isZero();
        jdbc.update("DELETE FROM users WHERE id = ?", alice.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_topics", Long.class)).isZero();
    }
}
