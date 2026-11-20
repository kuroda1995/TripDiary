package com.tripdiary.board.dto;

import com.tripdiary.board.BoardTopic;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.function.UnaryOperator;

/** 一覧用のトピック（本文は先頭のみ）。 */
@Schema(description = "一覧用の掲示板トピック")
public record BoardTopicSummaryResponse(
        @Schema(example = "12") long id,
        @Schema(example = "紅葉の時期におすすめのお寺を教えてください") String title,
        @Schema(description = "本文の先頭（最大120文字、省略時は末尾に…）") String bodyExcerpt,
        UserResponse author,
        @Schema(description = "返信の件数", example = "3") long replyCount,
        @Schema(description = "最後に書き込みがあった日時（作成または最新の返信）") Instant lastActivityAt,
        Instant createdAt) {

    static final int EXCERPT_LENGTH = 120;

    public static BoardTopicSummaryResponse from(
            BoardTopic topic, long replyCount, UnaryOperator<String> photoUrl) {
        return new BoardTopicSummaryResponse(
                topic.getId(),
                topic.getTitle(),
                excerpt(topic.getBody()),
                UserResponse.from(topic.getUser(), photoUrl),
                replyCount,
                topic.getLastActivityAt(),
                topic.getCreatedAt());
    }

    /** 一覧に出す本文の先頭（コミュニティの説明文にも使う）。 */
    public static String excerpt(String text) {
        String normalized = text.strip();
        if (normalized.codePointCount(0, normalized.length()) <= EXCERPT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, EXCERPT_LENGTH)) + "…";
    }
}
