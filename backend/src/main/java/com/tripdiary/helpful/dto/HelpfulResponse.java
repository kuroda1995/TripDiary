package com.tripdiary.helpful.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 参考になったの付け外しの結果。 */
@Schema(description = "参考になったの状態")
public record HelpfulResponse(
        @Schema(example = "1024") long spotId,
        @Schema(description = "閲覧者が付けているか") boolean helpful,
        @Schema(description = "参考になったの件数", example = "5") long helpfulCount) {}
