package com.tripdiary.spot;

import com.tripdiary.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 一覧のページング用カーソル（最後に返したスポットの投稿日時と ID）。
 *
 * <p>クライアントには中身を意識させないよう Base64URL でエンコードした文字列として渡す。
 */
public record SpotCursor(Instant createdAt, long id) {

    static final String INVALID_CURSOR = "INVALID_CURSOR";
    private static final String SEPARATOR = "|";

    public static SpotCursor of(Spot spot) {
        return new SpotCursor(spot.getCreatedAt(), spot.getId());
    }

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws BadRequestException 形式が不正な場合（INVALID_CURSOR）
     */
    public static SpotCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            if (separator < 0) {
                throw invalid();
            }
            return new SpotCursor(
                    Instant.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException(INVALID_CURSOR, "ページの指定が正しくありません");
    }
}
