package com.tripdiary.like.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 行ってみたい！を付けた・外した後の状態。 */
@Schema(description = "行ってみたい！の状態")
public record LikeResponse(
        @Schema(example = "1024") long spotId,
        @Schema(description = "自分が付けているか") boolean liked,
        @Schema(description = "件数", example = "13") long likeCount) {}
