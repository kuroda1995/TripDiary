package com.tripdiary.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** フォロー / フォロワー・プロフィール・タイムライン API の結合テスト（docs/qa_test_plan.md 4.5）。 */
@IntegrationTest
class FollowIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice;
    private TestAccount bob;
    private TestAccount carol;
    private long kyoto;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        bob = ApiTestClient.signup(mockMvc, "bob");
        carol = ApiTestClient.signup(mockMvc, "carol");
        kyoto = jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
    }

    private ResultActions follow(TestAccount account, String username) throws Exception {
        return mockMvc.perform(account.authorize(put("/api/v1/users/" + username + "/follow")));
    }

    private ResultActions unfollow(TestAccount account, String username) throws Exception {
        return mockMvc.perform(account.authorize(delete("/api/v1/users/" + username + "/follow")));
    }

    /** フォローした日時を固定する（並び順のテスト用）。 */
    private void followAt(TestAccount follower, TestAccount followee, Instant at) {
        jdbc.update(
                "INSERT INTO follows (follower_id, followee_id, created_at) VALUES (?, ?, ?)",
                follower.id(),
                followee.id(),
                Timestamp.from(at));
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

    private void setSpotCreatedAt(long spotId, Instant at) {
        jdbc.update("UPDATE spots SET created_at = ? WHERE id = ?", Timestamp.from(at), spotId);
    }

    @Nested
    @DisplayName("フォロー・解除")
    class Toggle {

        @Test
        @DisplayName("フォローすると following=true・フォロワー数が増え、解除すると元に戻る。プロフィールの件数も変わる")
        void followAndUnfollow() throws Exception {
            follow(bob, "alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.following").value(true))
                    .andExpect(jsonPath("$.followerCount").value(1));
            follow(carol, "alice").andExpect(jsonPath("$.followerCount").value(2));

            mockMvc.perform(bob.authorize(get("/api/v1/users/alice")))
                    .andExpect(jsonPath("$.followerCount").value(2))
                    .andExpect(jsonPath("$.followingCount").value(0))
                    .andExpect(jsonPath("$.followedByMe").value(true));
            mockMvc.perform(get("/api/v1/users/bob"))
                    .andExpect(jsonPath("$.followingCount").value(1))
                    .andExpect(jsonPath("$.followerCount").value(0));

            unfollow(bob, "alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.following").value(false))
                    .andExpect(jsonPath("$.followerCount").value(1));
            mockMvc.perform(bob.authorize(get("/api/v1/users/alice")))
                    .andExpect(jsonPath("$.followedByMe").value(false));
        }

        @Test
        @DisplayName("二重にフォローしても1件のままで、フォローしていないのに解除しても成功する（冪等）")
        void idempotent() throws Exception {
            follow(bob, "alice").andExpect(jsonPath("$.followerCount").value(1));
            follow(bob, "alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followerCount").value(1));
            unfollow(carol, "alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followerCount").value(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM follows", Long.class))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("自分自身はフォロー・解除できない（400 CANNOT_FOLLOW_SELF）")
        void cannotFollowSelf() throws Exception {
            follow(alice, "alice")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
            // ユーザー名の大文字・小文字が違っても自分自身と判定する
            follow(alice, "ALICE").andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
            unfollow(alice, "alice").andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM follows", Long.class)).isZero();
        }

        @Test
        @DisplayName("ユーザー名は大文字・小文字を区別せず、レスポンスは登録時の表記で返す")
        void caseInsensitive() throws Exception {
            follow(bob, "Alice")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"));
        }

        @Test
        @DisplayName("存在しないユーザーは 404 USER_NOT_FOUND")
        void unknownUser() throws Exception {
            follow(bob, "nobody")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
            unfollow(bob, "nobody").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("未ログインではフォロー・解除・タイムライン取得ができない（401）")
        void requiresLogin() throws Exception {
            mockMvc.perform(put("/api/v1/users/alice/follow")).andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/v1/users/alice/follow"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/timeline")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ユーザーを削除するとフォロー関係も削除される")
        void cascadeOnUserDelete() throws Exception {
            follow(bob, "alice");
            follow(alice, "carol");

            jdbc.update("DELETE FROM users WHERE id = ?", alice.id());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM follows", Long.class)).isZero();
        }
    }

    @Nested
    @DisplayName("プロフィール")
    class Profile {

        @Test
        @DisplayName("未ログインでも見られる。自己紹介・アイコンを含み、followedByMe は false")
        void anonymous() throws Exception {
            mockMvc.perform(
                            alice.authorize(patch("/api/v1/users/me"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"bio\":\"週末は温泉めぐり\"}"))
                    .andExpect(status().isOk());
            follow(bob, "alice");

            mockMvc.perform(get("/api/v1/users/alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(alice.id()))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.displayName").value("alice さん"))
                    .andExpect(jsonPath("$.bio").value("週末は温泉めぐり"))
                    .andExpect(jsonPath("$.avatarUrl").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.followerCount").value(1))
                    .andExpect(jsonPath("$.followedByMe").value(false))
                    // メールアドレスは他人に見せない
                    .andExpect(jsonPath("$.email").doesNotExist());
        }

        @Test
        @DisplayName("投稿数は公開投稿のみ数え、本人が見る場合は非公開も含める")
        void spotCount() throws Exception {
            createSpot(alice, "清水寺", "PUBLIC");
            createSpot(alice, "伏見稲荷", "PUBLIC");
            createSpot(alice, "秘密の場所", "PRIVATE");

            mockMvc.perform(get("/api/v1/users/alice")).andExpect(jsonPath("$.spotCount").value(2));
            mockMvc.perform(bob.authorize(get("/api/v1/users/alice")))
                    .andExpect(jsonPath("$.spotCount").value(2));
            mockMvc.perform(alice.authorize(get("/api/v1/users/alice")))
                    .andExpect(jsonPath("$.spotCount").value(3))
                    .andExpect(jsonPath("$.followedByMe").value(false));
        }

        @Test
        @DisplayName("存在しないユーザーは 404 USER_NOT_FOUND")
        void unknownUser() throws Exception {
            mockMvc.perform(get("/api/v1/users/nobody"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("/users/me は従来どおり本人の情報を返し、未ログインでは 401")
        void meIsNotAProfile() throws Exception {
            mockMvc.perform(alice.authorize(get("/api/v1/users/me")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("alice@example.com"));
            mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("フォロー中一覧・フォロワー一覧")
    class Lists {

        @Test
        @DisplayName("フォローした日時の新しい順に並び、未ログインでも見られる")
        void order() throws Exception {
            followAt(alice, bob, BASE);
            followAt(alice, carol, BASE.plusSeconds(60));
            followAt(bob, carol, BASE.plusSeconds(30));

            mockMvc.perform(get("/api/v1/users/alice/following"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].username", Matchers.contains("carol", "bob")))
                    .andExpect(jsonPath("$.items[0].displayName").value("carol さん"))
                    .andExpect(jsonPath("$.items[0].avatarUrl").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
            mockMvc.perform(get("/api/v1/users/carol/followers"))
                    .andExpect(jsonPath("$.items[*].username", Matchers.contains("alice", "bob")));
            mockMvc.perform(get("/api/v1/users/carol/following"))
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("各行の followedByMe は閲覧者がその人をフォローしているか（未ログインは false）")
        void followedByMe() throws Exception {
            follow(alice, "bob");
            follow(alice, "carol");
            follow(bob, "carol");

            mockMvc.perform(bob.authorize(get("/api/v1/users/alice/following")))
                    .andExpect(
                            jsonPath(
                                    "$.items[?(@.username == 'carol')].followedByMe",
                                    Matchers.contains(true)))
                    // 自分自身の行は false（画面ではボタンを出さない）
                    .andExpect(
                            jsonPath(
                                    "$.items[?(@.username == 'bob')].followedByMe",
                                    Matchers.contains(false)));
            mockMvc.perform(get("/api/v1/users/alice/following"))
                    .andExpect(
                            jsonPath("$.items[*].followedByMe", Matchers.contains(false, false)));
        }

        @Test
        @DisplayName("カーソルで続きを取得でき、同じ日時でも漏れ・重複なく全件たどれる")
        void pagination() throws Exception {
            List<TestAccount> followers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                TestAccount follower = ApiTestClient.signup(mockMvc, "fan" + i);
                followers.add(follower);
                // 2件ずつ同じ日時にする
                followAt(follower, alice, BASE.plusSeconds(i / 2));
            }

            List<String> seen = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                String url =
                        "/api/v1/users/alice/followers?limit=2"
                                + (cursor == null ? "" : "&cursor=" + cursor);
                String body =
                        mockMvc.perform(get(url))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                seen.addAll(JsonPath.read(body, "$.items[*].username"));
                cursor = JsonPath.read(body, "$.nextCursor");
                pages++;
            } while (cursor != null);

            assertThat(pages).isEqualTo(3);
            assertThat(seen).containsExactly("fan4", "fan3", "fan2", "fan1", "fan0");
            assertThat(followers).hasSize(5);
        }

        @Test
        @DisplayName("不正なカーソルは 400 INVALID_CURSOR、limit の範囲外は 400、存在しないユーザーは 404")
        void errors() throws Exception {
            mockMvc.perform(get("/api/v1/users/alice/followers?cursor=broken"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            mockMvc.perform(get("/api/v1/users/alice/following?limit=51"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(get("/api/v1/users/nobody/followers"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("タイムライン")
    class Timeline {

        @Test
        @DisplayName("フォロー中ユーザーの公開投稿のみが新着順に出る（自分・未フォロー・非公開は出ない）")
        void onlyFollowedPublicSpots() throws Exception {
            long bobOld = createSpot(bob, "bob の古い投稿", "PUBLIC");
            long carolNew = createSpot(carol, "carol の新しい投稿", "PUBLIC");
            long bobPrivate = createSpot(bob, "bob の非公開", "PRIVATE");
            long aliceOwn = createSpot(alice, "alice 自身の投稿", "PUBLIC");
            TestAccount dave = ApiTestClient.signup(mockMvc, "dave");
            long daveSpot = createSpot(dave, "未フォローの人", "PUBLIC");
            setSpotCreatedAt(bobOld, BASE);
            setSpotCreatedAt(carolNew, BASE.plusSeconds(60));
            setSpotCreatedAt(bobPrivate, BASE.plusSeconds(90));
            setSpotCreatedAt(aliceOwn, BASE.plusSeconds(120));
            setSpotCreatedAt(daveSpot, BASE.plusSeconds(150));

            follow(alice, "bob");
            follow(alice, "carol");
            mockMvc.perform(bob.authorize(put("/api/v1/spots/" + carolNew + "/like")))
                    .andExpect(status().isOk());

            mockMvc.perform(alice.authorize(get("/api/v1/timeline")))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath(
                                    "$.items[*].id",
                                    Matchers.contains((int) carolNew, (int) bobOld)))
                    // 形式はスポットの新着一覧と同じ
                    .andExpect(jsonPath("$.items[0].title").value("carol の新しい投稿"))
                    .andExpect(jsonPath("$.items[0].author.username").value("carol"))
                    .andExpect(jsonPath("$.items[0].likeCount").value(1))
                    .andExpect(jsonPath("$.items[0].likedByMe").value(false))
                    .andExpect(jsonPath("$.nextCursor").value(Matchers.nullValue()));
        }

        @Test
        @DisplayName("誰もフォローしていなければ空で、フォロー解除した人の投稿は出なくなる")
        void emptyAndUnfollow() throws Exception {
            createSpot(bob, "清水寺", "PUBLIC");

            mockMvc.perform(alice.authorize(get("/api/v1/timeline")))
                    .andExpect(jsonPath("$.items").isEmpty());
            follow(alice, "bob");
            mockMvc.perform(alice.authorize(get("/api/v1/timeline")))
                    .andExpect(jsonPath("$.items.length()").value(1));
            unfollow(alice, "bob");
            mockMvc.perform(alice.authorize(get("/api/v1/timeline")))
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("カーソルで続きを取得でき、同じ投稿日時でも漏れ・重複なく全件たどれる")
        void pagination() throws Exception {
            follow(alice, "bob");
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                long id = createSpot(bob, "spot" + i, "PUBLIC");
                setSpotCreatedAt(id, BASE.plusSeconds(i / 2));
                ids.add(id);
            }

            List<Integer> seen = new ArrayList<>();
            String cursor = null;
            do {
                String url =
                        "/api/v1/timeline?limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
                String body =
                        mockMvc.perform(alice.authorize(get(url)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                seen.addAll(JsonPath.read(body, "$.items[*].id"));
                cursor = JsonPath.read(body, "$.nextCursor");
            } while (cursor != null);

            assertThat(seen)
                    .containsExactly(
                            ids.get(4).intValue(),
                            ids.get(3).intValue(),
                            ids.get(2).intValue(),
                            ids.get(1).intValue(),
                            ids.get(0).intValue());
        }

        @Test
        @DisplayName("不正なカーソルは 400 INVALID_CURSOR")
        void invalidCursor() throws Exception {
            mockMvc.perform(alice.authorize(get("/api/v1/timeline?cursor=broken")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
    }
}
