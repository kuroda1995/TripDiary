package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripdiary.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpotCursorTest {

    @Test
    @DisplayName("エンコードしたカーソルをデコードすると元の投稿日時（マイクロ秒）と ID に戻る")
    void roundTrip() {
        SpotCursor cursor = new SpotCursor(Instant.parse("2026-11-11T03:15:42.123456Z"), 1024L);

        String encoded = cursor.encode();

        assertThat(encoded).matches("^[A-Za-z0-9_-]+$");
        assertThat(SpotCursor.decode(encoded)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64!", "", "bm8tc2VwYXJhdG9y"})
    @DisplayName("形式が不正なカーソルは INVALID_CURSOR")
    void rejectsMalformed(String encoded) {
        assertInvalid(encoded);
    }

    @Test
    @DisplayName("日時や ID が壊れているカーソルは INVALID_CURSOR")
    void rejectsBrokenContent() {
        assertInvalid(encode("not-a-date|1"));
        assertInvalid(encode("2026-11-11T00:00:00Z|abc"));
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalid(String encoded) {
        assertThatThrownBy(() -> SpotCursor.decode(encoded))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(SpotCursor.INVALID_CURSOR);
    }
}
