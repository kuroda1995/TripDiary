package com.tripdiary.status.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** マイリスト（更新日時の新しい順）の1ページ。 */
@Schema(description = "マイリスト")
public record MyListPageResponse(
        List<MyListItemResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
