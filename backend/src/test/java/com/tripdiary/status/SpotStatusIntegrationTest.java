package com.tripdiary.status;

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
import java.sql.Timestamp;
import java.time.Instant;
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

/** ステータス管理（マイリスト）API の結合テスト（docs/qa_test_plan.md 4.7）。 */
@IntegrationTest
class SpotStatusIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

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

    private ResultActions setStatus(TestAccount account, long spotId, String status)
            throws Exception {
        return mockMvc.perform(
                account.authorize(put("/api/v1/spots/" + spotId + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions clearStatus(TestAccount account, long spotId) throws Exception {
        return mockMvc.perform(account.authorize(delete("/api/v1/spots/" + spotId + "/status")));
    }

    private ResultActions myList(TestAccount account, String query) throws Exception {
        return mockMvc.perform(account.authorize(get("/api/v1/me/spot-statuses?" + query)));
    }

    private void setUpdatedAt(TestAccount account, long spotId, Instant at) {
        jdbc.update(
                "UPDATE user_spot_statuses SET updated_at = ? WHERE user_id = ? AND spot_id = ?",
                Timestamp.from(at),
                account.id(),
                spotId);
    }

    @Nested
    @DisplayName("設定・変更・解除")
    class SetAndClear {

        @Test
        @DisplayName("行きたい → 訪問済み → 行きたい と変えると、訪問日時が設定・消去される。詳細の myStatus も変わる")
        void switchStatus() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");

            setStatus(bob, spot, "WANT_TO_GO")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.spotId").value(spot))
                    .andExpect(jsonPath("$.status").value("WANT_TO_GO"))
                    .andExpect(jsonPath("$.visitedAt").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty());
            mockMvc.perform(bob.authorize(get("/api/v1/spots/" + spot)))
                    .andExpect(jsonPath("$.myStatus").value("WANT_TO_GO"));

            setStatus(bob, spot, "VISITED")
                    .andExpect(jsonPath("$.status").value("VISITED"))
                    .andExpect(jsonPath("$.visitedAt").isNotEmpty());
            mockMvc.perform(bob.authorize(get("/api/v1/spots")))
                    .andExpect(jsonPath("$.items[0].myStatus").value("VISITED"));

            setStatus(bob, spot, "WANT_TO_GO")
                    .andExpect(jsonPath("$.visitedAt").value(Matchers.nullValue()));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_spot_statuses", Long.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("同じ値で設定し直しても成功し、訪問日時・更新日時は変わらない")
        void sameValueKeepsDates() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");
            String first =
                    setStatus(bob, spot, "VISITED").andReturn().getResponse().getContentAsString();

            String second =
                    setStatus(bob, spot, "VISITED")
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat((String) JsonPath.read(second, "$.visitedAt"))
                    .isEqualTo(JsonPath.read(first, "$.visitedAt"));
            assertThat((String) JsonPath.read(second, "$.updatedAt"))
                    .isEqualTo(JsonPath.read(first, "$.updatedAt"));
        }

        @Test
        @DisplayName("解除すると 204 で myStatus が null になり、未設定でも 204（冪等）")
        void clear() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");
            setStatus(bob, spot, "VISITED");

            clearStatus(bob, spot).andExpect(status().isNoContent());
            mockMvc.perform(bob.authorize(get("/api/v1/spots/" + spot)))
                    .andExpect(jsonPath("$.myStatus").value(Matchers.nullValue()));
            clearStatus(bob, spot).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("自分の投稿・自分の非公開投稿にも付けられ、他人の非公開投稿・存在しない投稿は 404 SPOT_NOT_FOUND")
        void visibility() throws Exception {
            long secret = createSpot(alice, "秘密の場所", "PRIVATE");

            setStatus(alice, secret, "VISITED").andExpect(status().isOk());
            setStatus(bob, secret, "VISITED")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"));
            clearStatus(bob, secret).andExpect(status().isNotFound());
            setStatus(bob, 999_999, "VISITED").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ステータスは本人にだけ返る（他人・未ログインには myStatus が null）")
        void onlyForOwner() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");
            setStatus(bob, spot, "VISITED");

            mockMvc.perform(alice.authorize(get("/api/v1/spots/" + spot)))
                    .andExpect(jsonPath("$.myStatus").value(Matchers.nullValue()));
            mockMvc.perform(get("/api/v1/spots/" + spot))
                    .andExpect(jsonPath("$.myStatus").value(Matchers.nullValue()));
        }

        @Test
        @DisplayName("不正な値・未指定は 400 VALIDATION_ERROR、未ログインは 401")
        void errors() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");

            setStatus(bob, spot, "FAVORITE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(
                            bob.authorize(put("/api/v1/spots/" + spot + "/status"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(
                            put("/api/v1/spots/" + spot + "/status")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"VISITED\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/me/spot-statuses?status=VISITED"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/me/spot-statuses/summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("スポットを削除するとステータスも削除される")
        void cascade() throws Exception {
            long spot = createSpot(alice, "清水寺", "PUBLIC");
            setStatus(bob, spot, "VISITED");

            mockMvc.perform(alice.authorize(delete("/api/v1/spots/" + spot)))
                    .andExpect(status().isNoContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_spot_statuses", Long.class))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("マイリスト・件数")
    class MyList {

        @Test
        @DisplayName("指定したステータスのスポットだけが、更新日時の新しい順にスポットの情報付きで返る")
        void listByStatus() throws Exception {
            long a = createSpot(alice, "A", "PUBLIC");
            long b = createSpot(alice, "B", "PUBLIC");
            long c = createSpot(alice, "C", "PUBLIC");
            setStatus(bob, a, "WANT_TO_GO");
            setStatus(bob, b, "WANT_TO_GO");
            setStatus(bob, c, "VISITED");
            setUpdatedAt(bob, a, BASE.plusSeconds(60));
            setUpdatedAt(bob, b, BASE);

            myList(bob, "status=WANT_TO_GO")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].spot.id", Matchers.contains((int) a, (int) b)))
                    .andExpect(jsonPath("$.items[0].status").value("WANT_TO_GO"))
                    .andExpect(jsonPath("$.items[0].visitedAt").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.items[0].updatedAt").value("2026-11-01T00:01:00Z"))
                    .andExpect(jsonPath("$.items[0].spot.title").value("A"))
                    .andExpect(jsonPath("$.items[0].spot.myStatus").value("WANT_TO_GO"))
                    .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
            myList(bob, "status=VISITED")
                    .andExpect(jsonPath("$.items[*].spot.id", Matchers.contains((int) c)))
                    .andExpect(jsonPath("$.items[0].visitedAt").isNotEmpty());
            mockMvc.perform(bob.authorize(get("/api/v1/me/spot-statuses/summary")))
                    .andExpect(jsonPath("$.visited").value(1))
                    .andExpect(jsonPath("$.wantToGo").value(2));
        }

        @Test
        @DisplayName("自分のリストだけが返り、他人のステータスは含まれない")
        void onlyOwnList() throws Exception {
            long spot = createSpot(alice, "A", "PUBLIC");
            setStatus(bob, spot, "VISITED");

            myList(alice, "status=VISITED").andExpect(jsonPath("$.items").isEmpty());
            mockMvc.perform(alice.authorize(get("/api/v1/me/spot-statuses/summary")))
                    .andExpect(jsonPath("$.visited").value(0))
                    .andExpect(jsonPath("$.wantToGo").value(0));
        }

        @Test
        @DisplayName("後から非公開になった他人の投稿はリスト・件数に出ないが、データは残り、公開に戻すと再び出る")
        void hiddenWhenMadePrivate() throws Exception {
            long spot = createSpot(alice, "A", "PUBLIC");
            long own = createSpot(bob, "自分の非公開", "PRIVATE");
            setStatus(bob, spot, "WANT_TO_GO");
            setStatus(bob, own, "WANT_TO_GO");

            jdbc.update("UPDATE spots SET visibility = 'PRIVATE' WHERE id = ?", spot);

            myList(bob, "status=WANT_TO_GO")
                    .andExpect(jsonPath("$.items[*].spot.id", Matchers.contains((int) own)));
            mockMvc.perform(bob.authorize(get("/api/v1/me/spot-statuses/summary")))
                    .andExpect(jsonPath("$.wantToGo").value(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_spot_statuses", Long.class))
                    .isEqualTo(2);

            jdbc.update("UPDATE spots SET visibility = 'PUBLIC' WHERE id = ?", spot);
            myList(bob, "status=WANT_TO_GO").andExpect(jsonPath("$.items.length()").value(2));
        }

        @Test
        @DisplayName("カーソルで続きを取得でき、同じ更新日時でも漏れ・重複なく全件たどれる")
        void pagination() throws Exception {
            List<Long> spots = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                long id = createSpot(alice, "spot" + i, "PUBLIC");
                setStatus(bob, id, "VISITED");
                setUpdatedAt(bob, id, BASE.plusSeconds(i / 2));
                spots.add(id);
            }

            List<Integer> seen = new ArrayList<>();
            String cursor = null;
            do {
                String body =
                        myList(
                                        bob,
                                        "status=VISITED&limit=2"
                                                + (cursor == null ? "" : "&cursor=" + cursor))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                seen.addAll(JsonPath.read(body, "$.items[*].spot.id"));
                cursor = JsonPath.read(body, "$.nextCursor");
            } while (cursor != null);

            assertThat(seen)
                    .containsExactly(
                            spots.get(4).intValue(),
                            spots.get(3).intValue(),
                            spots.get(2).intValue(),
                            spots.get(1).intValue(),
                            spots.get(0).intValue());
        }

        @Test
        @DisplayName("status の指定がない・不正な値・不正なカーソルは 400")
        void errors() throws Exception {
            mockMvc.perform(bob.authorize(get("/api/v1/me/spot-statuses")))
                    .andExpect(status().isBadRequest());
            myList(bob, "status=FAVORITE").andExpect(status().isBadRequest());
            myList(bob, "status=VISITED&cursor=broken")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
    }
}
