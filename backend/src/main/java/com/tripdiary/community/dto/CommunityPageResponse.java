package com.tripdiary.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** コミュニティ一覧のページ。 */
@Schema(description = "コミュニティの一覧（カーソルページング）")
public record CommunityPageResponse(
        List<CommunitySummaryResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
