package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 一覧取得で N+1 が発生していないことを、Hibernate の統計で発行 SQL 数を数えて確認する（NFR-PERF-03）。 */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SpotQueryCountIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("20件の一覧を取得しても SQL は件数に比例せず一定回数で済む")
    void listDoesNotCauseNPlusOne() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        long kyoto =
                jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
        long osaka =
                jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'osaka'", Long.class);
        for (int i = 0; i < 20; i++) {
            // 投稿者もエリアタグも分散させ、関連の遅延読み込みが件数分発生しうる状態にする
            TestAccount author = ApiTestClient.signup(mockMvc, "user" + i);
            mockMvc.perform(
                            author.authorize(post("/api/v1/spots"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"title":"spot%d","locationName":"どこか","areaTagIds":[%d,%d],"visibility":"PUBLIC"}
                                            """
                                                    .formatted(i, kyoto, osaka)))
                    .andExpect(status().isCreated());
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/api/v1/spots").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20));

        // スポット + 投稿者（fetch join）で1回、エリアタグのまとめて取得で1回
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }
}
