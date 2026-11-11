package com.tripdiary.status.dto;

import com.tripdiary.spot.SpotStatus;
import com.tripdiary.status.SpotStatusRepository.StatusRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 設定後のステータス。 */
@Schema(description = "ステータス")
public record StatusResponse(
        @Schema(example = "1024") long spotId,
        SpotStatus status,
        @Schema(description = "「訪問済み」にした日時（それ以外は null）", nullable = true) Instant visitedAt,
        Instant updatedAt) {

    public static StatusResponse from(StatusRow row) {
        return new StatusResponse(row.spotId(), row.status(), row.visitedAt(), row.updatedAt());
    }
}
