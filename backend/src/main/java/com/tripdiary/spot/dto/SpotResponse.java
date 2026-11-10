package com.tripdiary.spot.dto;

import com.tripdiary.area.dto.AreaTagSummary;
import com.tripdiary.spot.Spot;
import com.tripdiary.spot.Visibility;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** スポット詳細。 */
@Schema(description = "スポット詳細")
public record SpotResponse(
        @Schema(example = "1024") long id,
        @Schema(example = "朝の清水寺") String title,
        @Schema(example = "清水寺（京都府京都市東山区）") String locationName,
        List<AreaTagSummary> areaTags,
        @Schema(nullable = true) String impression,
        @Schema(nullable = true, example = "2026-10-03") LocalDate visitedOn,
        Visibility visibility,
        UserResponse author,
        Instant createdAt,
        Instant updatedAt) {

    public static SpotResponse from(Spot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getTitle(),
                spot.getLocationName(),
                sortedTags(spot),
                spot.getImpression(),
                spot.getVisitedOn(),
                spot.getVisibility(),
                UserResponse.from(spot.getUser()),
                spot.getCreatedAt(),
                spot.getUpdatedAt());
    }

    /** エリアタグは表示順（地方 → 都道府県、各階層の並び順）で返す。 */
    static List<AreaTagSummary> sortedTags(Spot spot) {
        return spot.getAreaTags().stream()
                .sorted(
                        Comparator.comparingInt((com.tripdiary.area.AreaTag t) -> t.getLevel())
                                .thenComparingInt(com.tripdiary.area.AreaTag::getSortOrder)
                                .thenComparing(com.tripdiary.area.AreaTag::getId))
                .map(AreaTagSummary::from)
                .toList();
    }
}
