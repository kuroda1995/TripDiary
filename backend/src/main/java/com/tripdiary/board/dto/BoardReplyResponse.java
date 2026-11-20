package com.tripdiary.board.dto;

import com.tripdiary.board.BoardReply;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.function.UnaryOperator;

/** 返信。 */
@Schema(description = "掲示板の返信")
public record BoardReplyResponse(
        @Schema(example = "301") long id,
        @Schema(example = "12") long topicId,
        @Schema(description = "本文（プレーンテキスト。改行を含む）", example = "東福寺の通天橋がおすすめです。") String body,
        UserResponse author,
        Instant createdAt) {

    public static BoardReplyResponse from(BoardReply reply, UnaryOperator<String> photoUrl) {
        return new BoardReplyResponse(
                reply.getId(),
                reply.getTopic().getId(),
                reply.getBody(),
                UserResponse.from(reply.getUser(), photoUrl),
                reply.getCreatedAt());
    }
}
