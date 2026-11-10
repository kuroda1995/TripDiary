package com.tripdiary.spot.dto;

import com.tripdiary.area.dto.AreaTagSummary;
import com.tripdiary.spot.Spot;
import com.tripdiary.spot.Visibility;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 一覧用のスポット（感想は先頭のみ）。 */
@Schema(description = "一覧用のスポット")
public record SpotSummaryResponse(
        @Schema(example = "1024") long id,
        @Schema(example = "朝の清水寺") String title,
        @Schema(example = "清水寺（京都府京都市東山区）") String locationName,
        List<AreaTagSummary> areaTags,
        @Schema(description = "感想の先頭（最大120文字、省略時は末尾に…）", nullable = true) String impressionExcerpt,
        @Schema(nullable = true) LocalDate visitedOn,
        Visibility visibility,
        UserResponse author,
        Instant createdAt) {

    static final int EXCERPT_LENGTH = 120;

    public static SpotSummaryResponse from(Spot spot) {
        return new SpotSummaryResponse(
                spot.getId(),
                spot.getTitle(),
                spot.getLocationName(),
                SpotResponse.sortedTags(spot),
                excerpt(spot.getImpression()),
                spot.getVisitedOn(),
                spot.getVisibility(),
                UserResponse.from(spot.getUser()),
                spot.getCreatedAt());
    }

    static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.strip();
        if (normalized.codePointCount(0, normalized.length()) <= EXCERPT_LENGTH) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, EXCERPT_LENGTH);
        return normalized.substring(0, end) + "…";
    }
}
