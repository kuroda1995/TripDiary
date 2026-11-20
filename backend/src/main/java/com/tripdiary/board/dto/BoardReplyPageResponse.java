package com.tripdiary.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 返信一覧のページ。 */
@Schema(description = "掲示板の返信の一覧（古い順・カーソルページング）")
public record BoardReplyPageResponse(
        List<BoardReplyResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
