package com.tripdiary.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** コメント一覧（古い順）の1ページ。 */
@Schema(description = "コメント一覧")
public record CommentPageResponse(
        List<CommentResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
