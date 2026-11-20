package com.tripdiary.community;

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

/** コミュニティ API の結合テスト（docs/features/12_communities.md 8章）。 */
@IntegrationTest
class CommunityIntegrationTest {

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

    private ResultActions postCommunity(
            TestAccount account, String name, String description, String categoriesJson)
            throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/communities"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"%s","description":"%s","categories":%s}
                                """
                                        .formatted(name, description, categoriesJson)));
    }

    private long createCommunity(TestAccount account, String name, String categoriesJson)
            throws Exception {
        String response =
                postCommunity(account, name, name + "の説明です", categoriesJson)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long createCommunity(TestAccount account, String name) throws Exception {
        return createCommunity(account, name, "[\"REGIONAL\"]");
    }

    private ResultActions join(TestAccount account, long communityId) throws Exception {
        return mockMvc.perform(
                account.authorize(put("/api/v1/communities/" + communityId + "/membership")));
    }

    private ResultActions leave(TestAccount account, long communityId) throws Exception {
        return mockMvc.perform(
                account.authorize(delete("/api/v1/communities/" + communityId + "/membership")));
    }

    private ResultActions postTopic(TestAccount account, long communityId, String title)
            throws Exception {
        return mockMvc.perform(
                account.authorize(post("/api/v1/communities/" + communityId + "/topics"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"body\":\"本文です\"}".formatted(title)));
    }

    private long createTopic(TestAccount account, long communityId, String title) throws Exception {
        String response =
                postTopic(account, communityId, title)
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

    @Nested
    @DisplayName("作成・詳細")
    class CreateAndGet {

        @Test
        @DisplayName("作成すると 201・Location を返し、作成者はオーナーとして参加済み（メンバー数1）になる")
        void create() throws Exception {
            String response =
                    postCommunity(
                                    alice,
                                    "  北陸・富山のお寺巡り同好会  ",
                                    "  瑞龍寺や勝興寺の情報を交換しましょう  ",
                                    "[\"BEGINNER_FRIENDLY\",\"REGIONAL\",\"REGIONAL\"]")
                            .andExpect(status().isCreated())
                            .andExpect(
                                    header().string(
                                                    "Location",
                                                    Matchers.matchesRegex(
                                                            ".*/api/v1/communities/\\d+")))
                            .andExpect(jsonPath("$.name").value("北陸・富山のお寺巡り同好会"))
                            .andExpect(jsonPath("$.description").value("瑞龍寺や勝興寺の情報を交換しましょう"))
                            // 重複は1つにまとめ、表示順（宣言順）に並べる
                            .andExpect(
                                    jsonPath(
                                            "$.categories",
                                            Matchers.contains("REGIONAL", "BEGINNER_FRIENDLY")))
                            .andExpect(jsonPath("$.owner.username").value("alice"))
                            .andExpect(jsonPath("$.memberCount").value(1))
                            .andExpect(jsonPath("$.topicCount").value(0))
                            .andExpect(jsonPath("$.joined").value(true))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            long id = ((Number) JsonPath.read(response, "$.id")).longValue();

            // 未ログインでも見られる（参加状態は false）
            mockMvc.perform(get("/api/v1/communities/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberCount").value(1))
                    .andExpect(jsonPath("$.joined").value(false));
            mockMvc.perform(alice.authorize(get("/api/v1/communities/" + id)))
                    .andExpect(jsonPath("$.joined").value(true));
        }

        @Test
        @DisplayName("同じ名前（英字の大文字・小文字は区別しない）は 409 COMMUNITY_NAME_TAKEN")
        void duplicateName() throws Exception {
            createCommunity(alice, "Zen Garden Club");

            postCommunity(bob, "zen garden club", "説明", "[\"GOSHUIN\"]")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COMMUNITY_NAME_TAKEN"));
        }

        @Test
        @DisplayName("名前・説明文が空や長すぎる、カテゴリが空・不明なときは 400 VALIDATION_ERROR")
        void validation() throws Exception {
            postCommunity(alice, " ", "説明", "[\"GOSHUIN\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("name"));
            postCommunity(alice, "あ".repeat(51), "説明", "[\"GOSHUIN\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("name"));
            postCommunity(alice, "名前", "あ".repeat(1001), "[\"GOSHUIN\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("description"));
            postCommunity(alice, "名前", "説明", "[]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value("categories"));
            postCommunity(alice, "名前", "説明", "[\"UNKNOWN\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("未ログインでは作成できない（401）。存在しないコミュニティは 404 COMMUNITY_NOT_FOUND")
        void unauthorizedAndNotFound() throws Exception {
            mockMvc.perform(
                            post("/api/v1/communities")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"name\":\"n\",\"description\":\"d\",\"categories\":[\"GOSHUIN\"]}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/communities/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_FOUND"));
            join(bob, 999_999).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/communities/999999/members"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/v1/communities/999999/topics"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("一覧・検索")
    class Search {

        private long toyama;
        private long goshuin;
        private long garden;

        @BeforeEach
        void setUp() throws Exception {
            toyama =
                    createCommunity(alice, "北陸・富山のお寺巡り同好会", "[\"REGIONAL\",\"BEGINNER_FRIENDLY\"]");
            goshuin = createCommunity(bob, "限定御朱印情報シェアの会", "[\"GOSHUIN\"]");
            garden = createCommunity(carol, "枯山水・日本庭園を愛でる会", "[\"ARCHITECTURE_GARDEN\"]");
        }

        @Test
        @DisplayName("条件なしは新着順。ログイン中は参加しているかも返す")
        void newest() throws Exception {
            mockMvc.perform(alice.authorize(get("/api/v1/communities")))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath(
                                    "$.items[*].id",
                                    Matchers.contains((int) garden, (int) goshuin, (int) toyama)))
                    .andExpect(jsonPath("$.items[2].joined").value(true))
                    .andExpect(jsonPath("$.items[0].joined").value(false))
                    .andExpect(
                            jsonPath("$.items[2].descriptionExcerpt").value("北陸・富山のお寺巡り同好会の説明です"))
                    .andExpect(jsonPath("$.items[2].owner.username").value("alice"));
        }

        @Test
        @DisplayName("キーワードは名前・説明文の部分一致（% は文字として扱う）")
        void keyword() throws Exception {
            mockMvc.perform(get("/api/v1/communities").param("q", " 御朱印 "))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains((int) goshuin)));
            mockMvc.perform(get("/api/v1/communities").param("q", "庭園"))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains((int) garden)));
            mockMvc.perform(get("/api/v1/communities").param("q", "%"))
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("カテゴリで絞り込める。不明なカテゴリは 400")
        void category() throws Exception {
            mockMvc.perform(get("/api/v1/communities").param("category", "BEGINNER_FRIENDLY"))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains((int) toyama)));
            mockMvc.perform(
                            get("/api/v1/communities")
                                    .param("category", "GOSHUIN")
                                    .param("q", "富山"))
                    .andExpect(jsonPath("$.items").isEmpty());
            mockMvc.perform(get("/api/v1/communities").param("category", "UNKNOWN"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("人気順はメンバー数の多い順で、カーソルで続きを取得できる")
        void popularWithPaging() throws Exception {
            join(bob, toyama).andExpect(status().isOk());
            join(carol, toyama).andExpect(status().isOk());
            join(alice, goshuin).andExpect(status().isOk());

            String first =
                    mockMvc.perform(
                                    get("/api/v1/communities")
                                            .param("sort", "popular")
                                            .param("limit", "2"))
                            .andExpect(
                                    jsonPath(
                                            "$.items[*].id",
                                            Matchers.contains((int) toyama, (int) goshuin)))
                            .andExpect(jsonPath("$.items[0].memberCount").value(3))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            get("/api/v1/communities")
                                    .param("sort", "popular")
                                    .param("limit", "2")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains((int) garden)))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
            mockMvc.perform(get("/api/v1/communities").param("cursor", "%%%"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
            mockMvc.perform(get("/api/v1/communities").param("sort", "members"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("参加・退出")
    class Membership {

        private long community;

        @BeforeEach
        void setUp() throws Exception {
            community = createCommunity(alice, "限定御朱印情報シェアの会");
        }

        @Test
        @DisplayName("参加・退出でメンバー数が増減し、どちらも冪等")
        void joinAndLeave() throws Exception {
            join(bob, community)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.joined").value(true))
                    .andExpect(jsonPath("$.memberCount").value(2));
            join(bob, community).andExpect(jsonPath("$.memberCount").value(2));

            mockMvc.perform(bob.authorize(get("/api/v1/communities/" + community)))
                    .andExpect(jsonPath("$.joined").value(true))
                    .andExpect(jsonPath("$.memberCount").value(2));

            leave(bob, community)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.joined").value(false))
                    .andExpect(jsonPath("$.memberCount").value(1));
            leave(bob, community).andExpect(jsonPath("$.memberCount").value(1));
        }

        @Test
        @DisplayName("オーナーは退出できない（409 OWNER_CANNOT_LEAVE）。未ログインは 401")
        void ownerCannotLeave() throws Exception {
            leave(alice, community)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
            mockMvc.perform(put("/api/v1/communities/" + community + "/membership"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("メンバー一覧は参加した順（オーナーが先頭）で、カーソルで続きを取得できる")
        void members() throws Exception {
            join(carol, community);
            join(bob, community);

            String first =
                    mockMvc.perform(
                                    get("/api/v1/communities/" + community + "/members")
                                            .param("limit", "2"))
                            .andExpect(status().isOk())
                            .andExpect(
                                    jsonPath(
                                            "$.items[*].user.username",
                                            Matchers.contains("alice", "carol")))
                            .andExpect(jsonPath("$.items[0].owner").value(true))
                            .andExpect(jsonPath("$.items[1].owner").value(false))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            get("/api/v1/communities/" + community + "/members")
                                    .param("limit", "2")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].user.username", Matchers.contains("bob")))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }
    }

    @Nested
    @DisplayName("コミュニティ内のトピック（内輪トーク）")
    class Topics {

        private long community;

        @BeforeEach
        void setUp() throws Exception {
            community = createCommunity(alice, "枯山水・日本庭園を愛でる会");
            join(bob, community);
        }

        @Test
        @DisplayName("メンバーはトピックを作れ、コミュニティの一覧にだけ出る（広場には出ない）")
        void memberCanPost() throws Exception {
            long topic = createTopic(bob, community, "龍安寺の石庭の見どころ");

            mockMvc.perform(get("/api/v1/communities/" + community + "/topics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].id", Matchers.contains((int) topic)));
            mockMvc.perform(get("/api/v1/board-topics")).andExpect(jsonPath("$.items").isEmpty());
            mockMvc.perform(get("/api/v1/board-topics/" + topic))
                    .andExpect(jsonPath("$.community.id").value(community))
                    .andExpect(jsonPath("$.community.name").value("枯山水・日本庭園を愛でる会"))
                    .andExpect(jsonPath("$.community.ownerId").value(alice.id()));
            mockMvc.perform(get("/api/v1/communities/" + community))
                    .andExpect(jsonPath("$.topicCount").value(1));
        }

        @Test
        @DisplayName("広場のトピックはコミュニティの一覧に出ず、community は null")
        void boardTopicIsSeparate() throws Exception {
            mockMvc.perform(
                            carol.authorize(post("/api/v1/board-topics"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"title\":\"広場の話題\",\"body\":\"本文\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.community").isEmpty());

            mockMvc.perform(get("/api/v1/communities/" + community + "/topics"))
                    .andExpect(jsonPath("$.items").isEmpty());
        }

        @Test
        @DisplayName("メンバー以外はトピックの作成・返信ができない（403 COMMUNITY_MEMBERSHIP_REQUIRED）が、閲覧はできる")
        void nonMemberCannotWrite() throws Exception {
            long topic = createTopic(bob, community, "おすすめの庭園");

            postTopic(carol, community, "参加していないけど投稿")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMMUNITY_MEMBERSHIP_REQUIRED"));
            postReply(carol, topic, "返信")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("COMMUNITY_MEMBERSHIP_REQUIRED"));
            mockMvc.perform(get("/api/v1/board-topics/" + topic + "/replies"))
                    .andExpect(status().isOk());

            // 参加すれば書き込める
            join(carol, community);
            postReply(carol, topic, "参加しました").andExpect(status().isCreated());
            // 退出すると書き込めなくなる
            leave(carol, community);
            postReply(carol, topic, "退出後").andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("オーナーはメンバーのトピック・返信を削除できる。他のメンバーはできない")
        void ownerModerates() throws Exception {
            join(carol, community);
            long topic = createTopic(bob, community, "おすすめの庭園");
            String reply =
                    postReply(carol, topic, "返信")
                            .andExpect(status().isCreated())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            long replyId = ((Number) JsonPath.read(reply, "$.id")).longValue();
            long other = createTopic(carol, community, "別のトピック");

            // carol はトピックの作成者でもオーナーでもないので bob のトピックは消せない
            mockMvc.perform(carol.authorize(delete("/api/v1/board-topics/" + topic)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(alice.authorize(delete("/api/v1/board-replies/" + replyId)))
                    .andExpect(status().isNoContent());
            mockMvc.perform(alice.authorize(delete("/api/v1/board-topics/" + topic)))
                    .andExpect(status().isNoContent());
            // bob は carol のトピックを消せない
            mockMvc.perform(bob.authorize(delete("/api/v1/board-topics/" + other)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("削除はオーナーのみ（他人は 403）。メンバー・トピック・返信もまとめて消える")
    void deleteCommunity() throws Exception {
        long community = createCommunity(alice, "北陸・富山のお寺巡り同好会");
        join(bob, community);
        long topic = createTopic(bob, community, "瑞龍寺の雪景色");
        postReply(alice, topic, "きれいですね").andExpect(status().isCreated());

        mockMvc.perform(bob.authorize(delete("/api/v1/communities/" + community)))
                .andExpect(status().isForbidden());
        mockMvc.perform(alice.authorize(delete("/api/v1/communities/" + community)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/communities/" + community)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community_members", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_topics", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM board_replies", Long.class)).isZero();
    }

    @Test
    @DisplayName("メンバーのユーザーを削除するとメンバー数が減り、オーナーを削除するとコミュニティも消える")
    void cascadeOnUserDelete() throws Exception {
        long community = createCommunity(alice, "北陸・富山のお寺巡り同好会");
        join(bob, community);

        jdbc.update("DELETE FROM users WHERE id = ?", bob.id());
        mockMvc.perform(get("/api/v1/communities/" + community))
                .andExpect(jsonPath("$.memberCount").value(1));

        jdbc.update("DELETE FROM users WHERE id = ?", alice.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM communities", Long.class)).isZero();
    }
}
