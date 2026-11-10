package com.tripdiary.area;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripdiary.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** エリアタグ API と初期データ（V2）の結合テスト。 */
@IntegrationTest
class AreaTagIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("未ログインでもエリアタグを階層構造（地方7 + 海外）で取得でき、キャッシュ可能")
    void listTree() throws Exception {
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Cache-Control", Matchers.containsString("max-age=3600")))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].name").value("北海道・東北"))
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[0].children[0].name").value("北海道"))
                .andExpect(jsonPath("$[3].slug").value("kansai"))
                .andExpect(jsonPath("$[3].children[*].slug", Matchers.hasItems("kyoto", "osaka")))
                .andExpect(jsonPath("$[6].slug").value("overseas"));
    }

    @Test
    @DisplayName("47都道府県がすべて登録されている")
    void all47PrefecturesExist() throws Exception {
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(jsonPath("$[0:6].children[*]", Matchers.hasSize(47)));
    }
}
