package com.tripdiary.comment.dto;

import com.tripdiary.comment.Comment;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.function.UnaryOperator;

/** コメント。 */
@Schema(description = "コメント")
public record CommentResponse(
        @Schema(example = "501") long id,
        @Schema(example = "1024") long spotId,
        @Schema(description = "本文（プレーンテキスト。改行を含む）", example = "何時ごろ行きましたか？") String body,
        UserResponse author,
        @Schema(description = "編集されたことがあるか") boolean edited,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数（投稿者のアイコン用）
     */
    public static CommentResponse from(Comment comment, UnaryOperator<String> photoUrl) {
        return new CommentResponse(
                comment.getId(),
                comment.getSpot().getId(),
                comment.getBody(),
                UserResponse.from(comment.getUser(), photoUrl),
                comment.isEdited(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
