package com.tripdiary.status.dto;

import com.tripdiary.spot.SpotStatus;
import com.tripdiary.spot.dto.SpotSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** マイリストの1件（ステータスとスポット）。 */
@Schema(description = "マイリストの項目")
public record MyListItemResponse(
        SpotStatus status,
        @Schema(nullable = true) Instant visitedAt,
        Instant updatedAt,
        SpotSummaryResponse spot) {}
