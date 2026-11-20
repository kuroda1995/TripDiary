package com.tripdiary.community;

import com.tripdiary.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * コミュニティ一覧のページング用カーソル（最後に返したコミュニティの作成日時・ID・メンバー数）。
 *
 * <p>新着順は作成日時と ID、人気順はメンバー数も使う。クライアントには中身を意識させないよう Base64URL でエンコードする。
 */
public record CommunityCursor(Instant createdAt, long id, long memberCount) {

    static final String INVALID_CURSOR = "INVALID_CURSOR";
    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = createdAt + SEPARATOR + id + SEPARATOR + memberCount;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws BadRequestException 形式が不正な場合（INVALID_CURSOR）
     */
    public static CommunityCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3) {
                throw invalid();
            }
            return new CommunityCursor(
                    Instant.parse(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException(INVALID_CURSOR, "ページの指定が正しくありません");
    }
}
