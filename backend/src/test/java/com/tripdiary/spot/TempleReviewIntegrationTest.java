package com.tripdiary.spot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** お寺の5段階評価・特徴タグの結合テスト（docs/features/09_temple_review.md 8章）。 */
@IntegrationTest
class TempleReviewIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private TestAccount alice;
    private long kyoto;
    private long toyama;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
        kyoto = jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
        toyama = jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'toyama'", Long.class);
    }

    /**
     * @param extra お寺の項目（"ratings":{...},"templeFeatures":[...] の形。なければ空文字）
     */
    private ResultActions postSpot(String title, long areaTagId, String visibility, String extra)
            throws Exception {
        return mockMvc.perform(
                alice.authorize(post("/api/v1/spots"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"title":"%s","locationName":"どこか","areaTagIds":[%d],"visibility":"%s"%s}
                                """
                                        .formatted(
                                                title,
                                                areaTagId,
                                                visibility,
                                                extra.isEmpty() ? "" : "," + extra)));
    }

    private long createSpot(String title, long areaTagId, String visibility, String extra)
            throws Exception {
        String response =
                postSpot(title, areaTagId, visibility, extra)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Nested
    @DisplayName("投稿・詳細")
    class CreateAndGet {

        @Test
        @DisplayName("評価と特徴タグを保存し、詳細・一覧に返す。タグは表示順に並べ、重複は1つにまとめる")
        void savesRatingsAndFeatures() throws Exception {
            long id =
                    createSpot(
                            "瑞龍寺",
                            toyama,
                            "PUBLIC",
                            """
                            "ratings":{"tranquility":5,"beauty":5,"worship":4,"access":3},
                            "templeFeatures":["QUIET","GOSHUIN","CULTURAL_PROPERTY","GOSHUIN"]
                            """);

            mockMvc.perform(get("/api/v1/spots/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ratings.tranquility").value(5))
                    .andExpect(jsonPath("$.ratings.beauty").value(5))
                    .andExpect(jsonPath("$.ratings.worship").value(4))
                    .andExpect(jsonPath("$.ratings.access").value(3))
                    .andExpect(
                            jsonPath(
                                    "$.templeFeatures",
                                    Matchers.contains("GOSHUIN", "CULTURAL_PROPERTY", "QUIET")));
            mockMvc.perform(get("/api/v1/spots"))
                    .andExpect(jsonPath("$.items[0].ratings.tranquility").value(5))
                    .andExpect(jsonPath("$.items[0].templeFeatures.length()").value(3));
        }

        @Test
        @DisplayName("評価・特徴タグを省略した投稿（お寺以外の旅行記録）は ratings=null・templeFeatures=[]")
        void optional() throws Exception {
            long id = createSpot("伏見稲荷", kyoto, "PUBLIC", "");

            mockMvc.perform(get("/api/v1/spots/" + id))
                    .andExpect(jsonPath("$.ratings").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.templeFeatures").isEmpty());
        }

        @Test
        @DisplayName("一部の項目だけ評価でき、未評価の項目は null")
        void partialRatings() throws Exception {
            long id = createSpot("清水寺", kyoto, "PUBLIC", "\"ratings\":{\"beauty\":4}");

            mockMvc.perform(get("/api/v1/spots/" + id))
                    .andExpect(jsonPath("$.ratings.beauty").value(4))
                    .andExpect(jsonPath("$.ratings.tranquility").value(Matchers.nullValue()));
        }

        @Test
        @DisplayName("更新で評価・特徴タグを置き換え、省略すると消える")
        void updateReplaces() throws Exception {
            long id =
                    createSpot(
                            "清水寺",
                            kyoto,
                            "PUBLIC",
                            "\"ratings\":{\"beauty\":4},\"templeFeatures\":[\"GARDEN\"]");

            mockMvc.perform(
                            alice.authorize(put("/api/v1/spots/" + id))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"title":"清水寺","locationName":"どこか","areaTagIds":[%d],"visibility":"PUBLIC",
                                             "ratings":{"access":2},"templeFeatures":["PARKING"]}
                                            """
                                                    .formatted(kyoto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ratings.beauty").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.ratings.access").value(2))
                    .andExpect(jsonPath("$.templeFeatures", Matchers.contains("PARKING")));

            mockMvc.perform(
                            alice.authorize(put("/api/v1/spots/" + id))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"title":"清水寺","locationName":"どこか","areaTagIds":[%d],"visibility":"PUBLIC"}
                                            """
                                                    .formatted(kyoto)))
                    .andExpect(jsonPath("$.ratings").value(Matchers.nullValue()))
                    .andExpect(jsonPath("$.templeFeatures").isEmpty());
        }

        @Test
        @DisplayName("評価が1〜5の範囲外なら 400 VALIDATION_ERROR（項目名つき）")
        void ratingOutOfRange() throws Exception {
            postSpot("清水寺", kyoto, "PUBLIC", "\"ratings\":{\"tranquility\":0,\"access\":6}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(
                            jsonPath(
                                    "$.details[*].field",
                                    Matchers.containsInAnyOrder(
                                            "ratings.tranquility", "ratings.access")));
        }

        @Test
        @DisplayName("存在しない特徴タグは 400 VALIDATION_ERROR")
        void unknownFeature() throws Exception {
            postSpot("清水寺", kyoto, "PUBLIC", "\"templeFeatures\":[\"ONSEN\"]")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("特徴タグでの絞り込み")
    class Filter {

        @BeforeEach
        void setUp() throws Exception {
            createSpot(
                    "瑞龍寺",
                    toyama,
                    "PUBLIC",
                    "\"templeFeatures\":[\"GOSHUIN\",\"GARDEN\",\"CULTURAL_PROPERTY\"]");
            createSpot("清水寺", kyoto, "PUBLIC", "\"templeFeatures\":[\"GOSHUIN\"]");
            createSpot("龍安寺", kyoto, "PUBLIC", "\"templeFeatures\":[\"GARDEN\",\"QUIET\"]");
            createSpot("秘密の寺", kyoto, "PRIVATE", "\"templeFeatures\":[\"GOSHUIN\",\"GARDEN\"]");
            createSpot("伏見稲荷", kyoto, "PUBLIC", "");
        }

        @Test
        @DisplayName("1つ指定するとそのタグを持つ公開投稿だけを返す（非公開は含めない）")
        void single() throws Exception {
            mockMvc.perform(get("/api/v1/spots").param("features", "GOSHUIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("清水寺", "瑞龍寺")));
        }

        @Test
        @DisplayName("複数指定するとすべてを持つ投稿だけを返す（AND）")
        void allOf() throws Exception {
            mockMvc.perform(get("/api/v1/spots").param("features", "GOSHUIN,GARDEN"))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("瑞龍寺")));
        }

        @Test
        @DisplayName("エリア・キーワード・人気順と組み合わせられる")
        void combined() throws Exception {
            mockMvc.perform(
                            get("/api/v1/spots")
                                    .param("features", "GARDEN")
                                    .param("areaIds", String.valueOf(kyoto)))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("龍安寺")));
            mockMvc.perform(get("/api/v1/spots").param("features", "GARDEN").param("q", "瑞龍"))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("瑞龍寺")));
            mockMvc.perform(
                            get("/api/v1/spots")
                                    .param("features", "GARDEN")
                                    .param("sort", "popular"))
                    .andExpect(
                            jsonPath(
                                    "$.items[*].title", Matchers.containsInAnyOrder("瑞龍寺", "龍安寺")));
        }

        @Test
        @DisplayName("カーソルで続きを取得できる")
        void paging() throws Exception {
            String first =
                    mockMvc.perform(
                                    get("/api/v1/spots")
                                            .param("features", "GOSHUIN")
                                            .param("limit", "1"))
                            .andExpect(jsonPath("$.items[*].title", Matchers.contains("清水寺")))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            mockMvc.perform(
                            get("/api/v1/spots")
                                    .param("features", "GOSHUIN")
                                    .param("limit", "1")
                                    .param("cursor", (String) JsonPath.read(first, "$.nextCursor")))
                    .andExpect(jsonPath("$.items[*].title", Matchers.contains("瑞龍寺")))
                    .andExpect(jsonPath("$.nextCursor").isEmpty());
        }

        @Test
        @DisplayName("存在しない特徴タグは 400 VALIDATION_ERROR")
        void unknown() throws Exception {
            mockMvc.perform(get("/api/v1/spots").param("features", "ONSEN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }
}
