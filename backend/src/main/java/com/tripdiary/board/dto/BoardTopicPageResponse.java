package com.tripdiary.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** トピック一覧のページ。 */
@Schema(description = "掲示板トピックの一覧（カーソルページング）")
public record BoardTopicPageResponse(
        List<BoardTopicSummaryResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
