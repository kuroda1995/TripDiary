package com.tripdiary.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** コメント API の結合テスト（docs/qa_test_plan.md 4.4）。 */
@IntegrationTest
class CommentIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice; // スポットの投稿者
    private TestAccount bob;
    private TestAccount carol;
    private long spot;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        bob = ApiTestClient.signup(mockMvc, "bob");
        carol = ApiTestClient.signup(mockMvc, "carol");
        spot = createSpot(alice, "PUBLIC");
    }

    private long createSpot(TestAccount account, String visibility) throws Exception {
        long kyoto =
                jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
        String response =
                mockMvc.perform(
                                account.authorize(post("/api/v1/spots"))
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

    private static String body(String text) {
        return "{\"body\":%s}".formatted(text == null ? "null" : "\"" + text + "\"");
    }

    private ResultActions submit(TestAccount account, long spotId, String text) throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/spots/" + spotId + "/comments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(text)));
    }

    private long postComment(TestAccount account, String text) throws Exception {
        String response =
                submit(account, spot, text)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions edit(TestAccount account, long commentId, String text) throws Exception {
        return mockMvc.perform(
                account.authorize(patch("/api/v1/comments/" + commentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(text)));
    }

    private ResultActions remove(TestAccount account, long commentId) throws Exception {
        return mockMvc.perform(account.authorize(delete("/api/v1/comments/" + commentId)));
    }

    @Nested
    @DisplayName("投稿・一覧")
    class PostAndList {

        @Test
        @DisplayName("投稿すると 201 と Location、前後の空白を除いた本文・投稿者・edited=false を返す")
        void create() throws Exception {
            submit(bob, spot, "  何時ごろ行きましたか？\\n朝ですか？  ")
                    .andExpect(status().isCreated())
                    .andExpect(
                            header().string(
                                            "Location",
                                            Matchers.matchesPattern("/api/v1/comments/\\d+")))
                    .andExpect(jsonPath("$.spotId").value(spot))
                    .andExpect(jsonPath("$.body").value("何時ごろ行きましたか？\n朝ですか？"))
                    .andExpect(jsonPath("$.author.username").value("bob"))
                    .andExpect(jsonPath("$.edited").value(false));
        }

        @Test
        @DisplayName("一覧は古い順で、カーソルで続きを重複・欠落なく取得でき、未ログインでも見られる")
        void listOldestFirstWithPaging() throws Exception {
            List<String> posted = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                postComment(i % 2 == 0 ? bob : carol, "comment" + i);
                posted.add("comment" + i);
            }

            List<String> collected = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                var request = get("/api/v1/spots/" + spot + "/comments").param("limit", "2");
                if (cursor != null) {
                    request.param("cursor", cursor);
                }
                String response =
                        mockMvc.perform(request)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                collected.addAll(JsonPath.read(response, "$.items[*].body"));
                cursor = JsonPath.read(response, "$.nextCursor");
                pages++;
            } while (cursor != null);

            assertThat(collected).containsExactlyElementsOf(posted);
            assertThat(pages).isEqualTo(3);
        }

        @Test
        @DisplayName("空・空白のみ・null・1001文字は VALIDATION_ERROR、1000文字ちょうどは可")
        void validation() throws Exception {
            for (String text : new String[] {"", "   ", null, "あ".repeat(1001)}) {
                submit(bob, spot, text)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                        .andExpect(jsonPath("$.details[0].field").value("body"));
            }
            submit(bob, spot, "あ".repeat(1000)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("HTML はそのままの文字列として保存・返却する（画面ではテキストとして表示する）")
        void htmlIsStoredAsText() throws Exception {
            submit(bob, spot, "<script>alert(1)</script>")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.body").value("<script>alert(1)</script>"));
        }

        @Test
        @DisplayName("他人の非公開スポット・存在しないスポットは閲覧も投稿も 404 SPOT_NOT_FOUND、本人は可")
        void privateSpot() throws Exception {
            long secret = createSpot(alice, "PRIVATE");

            mockMvc.perform(get("/api/v1/spots/" + secret + "/comments"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
            submit(bob, secret, "x").andExpect(status().isNotFound());
            submit(bob, 999_999, "x").andExpect(status().isNotFound());
            submit(alice, secret, "自分用メモ").andExpect(status().isCreated());
            mockMvc.perform(alice.authorize(get("/api/v1/spots/" + secret + "/comments")))
                    .andExpect(jsonPath("$.items[0].body").value("自分用メモ"));
        }

        @Test
        @DisplayName("未ログインでは投稿・編集・削除できない（401）")
        void requiresLogin() throws Exception {
            long comment = postComment(bob, "x");

            mockMvc.perform(
                            post("/api/v1/spots/" + spot + "/comments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body("x")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            patch("/api/v1/comments/" + comment)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body("y")))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/v1/comments/" + comment))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("スポットの詳細・一覧にコメント件数が入る")
        void commentCount() throws Exception {
            postComment(bob, "1");
            postComment(carol, "2");

            mockMvc.perform(get("/api/v1/spots/" + spot))
                    .andExpect(jsonPath("$.commentCount").value(2));
            mockMvc.perform(get("/api/v1/spots"))
                    .andExpect(jsonPath("$.items[0].commentCount").value(2));
        }
    }

    @Nested
    @DisplayName("編集・削除")
    class EditAndDelete {

        @Test
        @DisplayName("本人は編集でき edited=true になる。同じ本文で保存しても edited のまま変わらない")
        void editOwnComment() throws Exception {
            long comment = postComment(bob, "誤字あり");

            edit(bob, comment, "同じ本文").andExpect(status().isOk());
            edit(bob, comment, "同じ本文")
                    .andExpect(jsonPath("$.body").value("同じ本文"))
                    .andExpect(jsonPath("$.edited").value(true));

            long untouched = postComment(bob, "そのまま");
            edit(bob, untouched, "そのまま").andExpect(jsonPath("$.edited").value(false));

            mockMvc.perform(get("/api/v1/spots/" + spot + "/comments"))
                    .andExpect(jsonPath("$.items[0].edited").value(true))
                    .andExpect(jsonPath("$.items[1].edited").value(false));
        }

        @Test
        @DisplayName("他人のコメントは、スポットの投稿者であっても編集できない（403）")
        void cannotEditOthers() throws Exception {
            long comment = postComment(bob, "bob のコメント");

            edit(carol, comment, "x")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            edit(alice, comment, "x").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("削除はコメントの投稿者とスポットの投稿者だけができ、他の人は 403")
        void deletePermissions() throws Exception {
            long byBob = postComment(bob, "bob");
            long byCarol = postComment(carol, "carol");

            remove(carol, byBob).andExpect(status().isForbidden());
            remove(bob, byBob).andExpect(status().isNoContent());
            // スポットの投稿者は他人のコメントも消せる（不適切なコメント対策）
            remove(alice, byCarol).andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM comments", Long.class)).isZero();
        }

        @Test
        @DisplayName("存在しないコメント・他人の非公開スポットのコメントは 404 COMMENT_NOT_FOUND")
        void notFound() throws Exception {
            long secret = createSpot(alice, "PRIVATE");
            String response =
                    submit(alice, secret, "メモ")
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            long hidden = ((Number) JsonPath.read(response, "$.id")).longValue();

            remove(bob, 999_999)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
            remove(bob, hidden).andExpect(status().isNotFound());
            edit(bob, hidden, "x").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("スポットを削除するとコメントも削除される")
        void cascadeOnSpotDelete() throws Exception {
            postComment(bob, "x");

            mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spot)))
                    .andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM comments", Long.class)).isZero();
        }
    }
}
