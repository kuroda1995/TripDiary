package com.tripdiary.status.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** ステータスごとの件数。 */
@Schema(description = "マイリストの件数")
public record StatusSummaryResponse(
        @Schema(description = "訪問済み", example = "34") long visited,
        @Schema(description = "行きたい", example = "12") long wantToGo) {}
