package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripdiary.common.exception.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PopularSpotCursorTest {

    private static final Instant T = Instant.parse("2026-11-01T00:00:00.123456Z");

    @Test
    @DisplayName("エンコードした値を元に戻せる（マイクロ秒まで保持）")
    void roundTrip() {
        PopularSpotCursor cursor = new PopularSpotCursor(12, T, 345);

        assertThat(PopularSpotCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    @DisplayName("新着順のカーソルとは互いに読み替えられない")
    void notInterchangeableWithNewest() {
        String newest = new SpotCursor(T, 1).encode();
        String popular = new PopularSpotCursor(3, T, 1).encode();

        assertThatThrownBy(() -> PopularSpotCursor.decode(newest)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> SpotCursor.decode(popular)).isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken!", "", "MXwy", "eHwyMDI2LTExLTAxVDAwOjAwOjAwWnwx"})
    @DisplayName("形式が不正なら INVALID_CURSOR")
    void invalid(String encoded) {
        assertThatThrownBy(() -> PopularSpotCursor.decode(encoded))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(SpotCursor.INVALID_CURSOR));
    }

    @Test
    @DisplayName("LIKE のワイルドカードとエスケープ文字はエスケープする")
    void escapeLike() {
        assertThat(SpotSearchRepository.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
        assertThat(SpotSearchRepository.escapeLike("温泉")).isEqualTo("温泉");
    }
}
