package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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

/** スポット検索（エリアタグ・キーワード・並び替え）の結合テスト（docs/qa_test_plan.md 4.6）。 */
@IntegrationTest
class SpotSearchIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice;
    private int spotCount;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        spotCount = 0;
    }

    private long tag(String slug) {
        return jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = ?", Long.class, slug);
    }

    /** スポットを作り、投稿日時を作成順に1分ずつずらす（後に作ったものほど新しい）。 */
    private long detailedSpot(String title, String location, String visibility, String... slugs)
            throws Exception {
        String tagIds =
                java.util.Arrays.stream(slugs)
                        .map(s -> String.valueOf(tag(s)))
                        .collect(Collectors.joining(","));
        String response =
                mockMvc.perform(
                                alice.authorize(post("/api/v1/spots"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"title":"%s","locationName":"%s","areaTagIds":[%s],"visibility":"%s"}
                                                """
                                                        .formatted(
                                                                title,
                                                                location,
                                                                tagIds,
                                                                visibility)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        setCreatedAt(id, BASE.plusSeconds(60L * spotCount++));
        return id;
    }

    private long spot(String title, String... slugs) throws Exception {
        return detailedSpot(title, "どこか", "PUBLIC", slugs);
    }

    private void setCreatedAt(long spotId, Instant at) {
        jdbc.update("UPDATE spots SET created_at = ? WHERE id = ?", Timestamp.from(at), spotId);
    }

    /** 行ってみたい！を指定した件数だけ付ける（付ける人は使い捨てのユーザー）。 */
    private void likes(long spotId, int count) {
        for (int i = 0; i < count; i++) {
            Long userId =
                    jdbc.queryForObject(
                            "INSERT INTO users (email, username, password_hash, display_name)"
                                    + " VALUES (?, ?, 'x', 'fan') RETURNING id",
                            Long.class,
                            "fan" + spotId + "_" + i + "@example.com",
                            "fan" + spotId + "_" + i);
            jdbc.update("INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)", userId, spotId);
        }
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/spots?" + query));
    }

    private static Integer[] ids(long... ids) {
        return LongStream.of(ids).mapToObj(id -> (int) id).toArray(Integer[]::new);
    }

    /** 全ページをたどって ID を集める。 */
    private List<Integer> collectAll(String query) throws Exception {
        List<Integer> seen = new ArrayList<>();
        String cursor = null;
        do {
            String body =
                    search(query + (cursor == null ? "" : "&cursor=" + cursor))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            seen.addAll(JsonPath.read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        return seen;
    }

    @Nested
    @DisplayName("エリアタグ")
    class AreaTags {

        @Test
        @DisplayName("単一タグで絞り込める")
        void singleTag() throws Exception {
            long kyoto = spot("清水寺", "kyoto");
            spot("中洲", "fukuoka");

            search("areaIds=" + tag("kyoto"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(kyoto))));
        }

        @Test
        @DisplayName("複数タグは OR 条件で、両方に当てはまる投稿も1件だけ返す")
        void multipleTagsWithoutDuplicates() throws Exception {
            long kyoto = spot("清水寺", "kyoto");
            long osaka = spot("通天閣", "osaka");
            long both = spot("京阪めぐり", "kyoto", "osaka");
            spot("中洲", "fukuoka");

            search("areaIds=" + tag("kyoto") + "," + tag("osaka"))
                    .andExpect(
                            jsonPath("$.items[*].id", Matchers.contains(ids(both, osaka, kyoto))));
        }

        @Test
        @DisplayName("親タグ（地方）を指定すると子タグの投稿も含まれ、子タグと同時に指定しても重複しない")
        void parentIncludesChildren() throws Exception {
            long kyoto = spot("清水寺", "kyoto");
            long osaka = spot("通天閣", "osaka");
            spot("中洲", "fukuoka");

            search("areaIds=" + tag("kansai"))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(osaka, kyoto))));
            search("areaIds=" + tag("kansai") + "&areaIds=" + tag("kyoto"))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(osaka, kyoto))));
        }

        @Test
        @DisplayName("非公開の投稿は出ない")
        void excludesPrivate() throws Exception {
            long visible = detailedSpot("公開", "どこか", "PUBLIC", "kyoto");
            detailedSpot("非公開", "どこか", "PRIVATE", "kyoto");

            // 投稿者本人が検索しても出ない（検索は公開投稿のみ）
            mockMvc.perform(alice.authorize(get("/api/v1/spots?areaIds=" + tag("kyoto"))))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(visible))));
        }

        @Test
        @DisplayName("存在しないタグは 400 AREA_TAG_NOT_FOUND、11個以上は 400 VALIDATION_ERROR、数値でなければ 400")
        void errors() throws Exception {
            search("areaIds=" + tag("kyoto") + ",999999")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AREA_TAG_NOT_FOUND"));
            String eleven =
                    LongStream.rangeClosed(1, 11)
                            .mapToObj(String::valueOf)
                            .collect(Collectors.joining(","));
            search("areaIds=" + eleven)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            search("areaIds=kyoto").andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("キーワード")
    class Keyword {

        @Test
        @DisplayName("タイトル・場所名の部分一致で、大文字・小文字を区別しない")
        void partialMatch() throws Exception {
            long byTitle = detailedSpot("黒川温泉の朝", "熊本", "PUBLIC", "kumamoto");
            long byLocation = detailedSpot("朝ごはん", "道後温泉本館", "PUBLIC", "ehime");
            long latin = detailedSpot("Onsen Tour", "Beppu", "PUBLIC", "oita");
            detailedSpot("清水寺", "京都", "PUBLIC", "kyoto");

            search("q=温泉")
                    .andExpect(
                            jsonPath("$.items[*].id", Matchers.contains(ids(byLocation, byTitle))));
            search("q=ONSEN").andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(latin))));
            // 前後の空白は無視する。空白だけなら絞り込まない
            mockMvc.perform(get("/api/v1/spots").param("q", " 温泉 "))
                    .andExpect(jsonPath("$.items.length()").value(2));
            mockMvc.perform(get("/api/v1/spots").param("q", "  "))
                    .andExpect(jsonPath("$.items.length()").value(4));
        }

        @Test
        @DisplayName("% や _ は文字そのものとして扱う")
        void wildcardsAreLiteral() throws Exception {
            long percent = spot("満足度100%の宿", "kyoto");
            spot("満足度1000の宿", "kyoto");
            long underscore = spot("a_b", "kyoto");
            spot("axb", "kyoto");

            mockMvc.perform(get("/api/v1/spots").param("q", "100%"))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(percent))));
            search("q=a_b")
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(underscore))));
        }

        @Test
        @DisplayName("エリアタグとキーワードは AND 条件")
        void andWithAreaTags() throws Exception {
            long kyotoOnsen = spot("京都の温泉", "kyoto");
            spot("大分の温泉", "oita");
            spot("清水寺", "kyoto");

            search("areaIds=" + tag("kansai") + "&q=温泉")
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(kyotoOnsen))));
        }

        @Test
        @DisplayName("101文字以上は 400 VALIDATION_ERROR")
        void tooLong() throws Exception {
            search("q=" + "あ".repeat(100)).andExpect(status().isOk());
            search("q=" + "あ".repeat(101))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("並び替え")
    class Sort {

        @Test
        @DisplayName("popular は行ってみたい！数の降順、同数なら新しい順。newest は新しい順")
        void order() throws Exception {
            long a = spot("A", "kyoto");
            long b = spot("B", "kyoto");
            long c = spot("C", "kyoto");
            long d = spot("D", "kyoto");
            likes(a, 3);
            likes(b, 1);
            likes(c, 3);

            search("sort=popular")
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(c, a, b, d))))
                    .andExpect(jsonPath("$.items[0].likeCount").value(3));
            search("sort=newest")
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(d, c, b, a))));
            search("sort=popular&areaIds=" + tag("kansai") + "&q=A")
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains(ids(a))));
        }

        @Test
        @DisplayName("人気順のページングで、同数・同時刻があっても重複・欠落なく全件たどれる")
        void popularPagination() throws Exception {
            List<Long> created = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                created.add(spot("spot" + i, "kyoto"));
            }
            // 同じ件数・同じ投稿日時の組を作る
            setCreatedAt(created.get(1), BASE);
            setCreatedAt(created.get(2), BASE);
            likes(created.get(0), 2);
            likes(created.get(1), 2);
            likes(created.get(2), 2);
            likes(created.get(3), 1);

            List<Integer> seen = collectAll("sort=popular&limit=2");

            assertThat(seen)
                    .containsExactly(
                            ids(
                                    created.get(2), // 2件・BASE・ID 大
                                    created.get(1), // 2件・BASE・ID 小
                                    created.get(0), // 2件・BASE
                                    created.get(3), // 1件
                                    created.get(6),
                                    created.get(5),
                                    created.get(4)));
        }

        @Test
        @DisplayName("条件付きの新着順もページングでき、条件なしの新着一覧と同じ並びになる")
        void newestPagination() throws Exception {
            List<Long> created = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                created.add(spot("spot" + i, "kyoto"));
            }
            spot("fukuoka", "fukuoka");

            assertThat(collectAll("areaIds=" + tag("kyoto") + "&limit=2"))
                    .containsExactly(
                            ids(
                                    created.get(4),
                                    created.get(3),
                                    created.get(2),
                                    created.get(1),
                                    created.get(0)));
            assertThat(collectAll("limit=2")).hasSize(6);
        }

        @Test
        @DisplayName("不正な sort は 400 VALIDATION_ERROR、並び順を取り違えたカーソルは 400 INVALID_CURSOR")
        void errors() throws Exception {
            for (int i = 0; i < 3; i++) {
                spot("spot" + i, "kyoto");
            }
            search("sort=likes")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            String newestCursor =
                    JsonPath.read(
                            search("limit=1").andReturn().getResponse().getContentAsString(),
                            "$.nextCursor");
            String popularCursor =
                    JsonPath.read(
                            search("sort=popular&limit=1")
                                    .andReturn()
                                    .getResponse()
                                    .getContentAsString(),
                            "$.nextCursor");
            search("sort=popular&cursor=" + newestCursor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            search("sort=newest&q=spot&cursor=" + popularCursor)
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            search("cursor=" + popularCursor).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }

        @Test
        @DisplayName("検索結果にも閲覧者の行ってみたい！の状態が入る")
        void likedByMe() throws Exception {
            long a = spot("A", "kyoto");
            mockMvc.perform(alice.authorize(put("/api/v1/spots/" + a + "/like")))
                    .andExpect(status().isOk());

            mockMvc.perform(alice.authorize(get("/api/v1/spots?sort=popular")))
                    .andExpect(jsonPath("$.items[0].likedByMe").value(true))
                    .andExpect(jsonPath("$.items[0].likeCount").value(1));
        }
    }
}
