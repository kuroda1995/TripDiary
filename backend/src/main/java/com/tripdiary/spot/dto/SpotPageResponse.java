package com.tripdiary.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** スポット一覧の1ページ。 */
@Schema(description = "スポット一覧の1ページ")
public record SpotPageResponse(
        List<SpotSummaryResponse> items,
        @Schema(description = "次のページを取得するカーソル。最後のページなら null", nullable = true) String nextCursor) {}
