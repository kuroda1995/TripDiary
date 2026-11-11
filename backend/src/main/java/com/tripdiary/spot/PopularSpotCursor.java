package com.tripdiary.spot;

import com.tripdiary.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 人気順のページング用カーソル（最後に返したスポットの行ってみたい！数・投稿日時・ID）。
 *
 * <p>新着順の {@link SpotCursor} とは区切りの数が違うため、並び順を取り違えたカーソルは INVALID_CURSOR になる。
 * ページをめくる間に件数が変わると、順位が動いて重複・欠落が起こり得る（画面側で同じ ID は表示しない）。
 */
public record PopularSpotCursor(long likeCount, Instant createdAt, long id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = likeCount + SEPARATOR + createdAt + SEPARATOR + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws BadRequestException 形式が不正な場合（INVALID_CURSOR）
     */
    public static PopularSpotCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) {
                throw invalid();
            }
            return new PopularSpotCursor(
                    Long.parseLong(parts[0]), Instant.parse(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException(SpotCursor.INVALID_CURSOR, "ページの指定が正しくありません");
    }
}
