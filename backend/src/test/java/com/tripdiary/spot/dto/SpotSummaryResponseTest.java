package com.tripdiary.spot.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotSummaryResponseTest {

    @Test
    @DisplayName("感想が120文字以内ならそのまま、超えたら120文字で切って…を付ける")
    void excerpt() {
        String exactly120 = "あ".repeat(120);

        assertThat(SpotSummaryResponse.excerpt(exactly120)).isEqualTo(exactly120);
        assertThat(SpotSummaryResponse.excerpt(exactly120 + "い")).isEqualTo(exactly120 + "…");
    }

    @Test
    @DisplayName("絵文字（サロゲートペア）の途中で切らない")
    void excerptKeepsSurrogatePairs() {
        String text = "😀".repeat(121);

        String excerpt = SpotSummaryResponse.excerpt(text);

        assertThat(excerpt).isEqualTo("😀".repeat(120) + "…");
    }

    @Test
    @DisplayName("感想が無い・空白のみなら null")
    void excerptOfBlank() {
        assertThat(SpotSummaryResponse.excerpt(null)).isNull();
        assertThat(SpotSummaryResponse.excerpt("  \n ")).isNull();
    }
}
