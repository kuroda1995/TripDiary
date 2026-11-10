package com.tripdiary.area.dto;

import com.tripdiary.area.AreaTag;
import io.swagger.v3.oas.annotations.media.Schema;

/** スポットに付いたエリアタグ。 */
@Schema(description = "エリアタグ")
public record AreaTagSummary(
        @Schema(example = "29") long id,
        @Schema(example = "京都") String name,
        @Schema(example = "kyoto") String slug) {

    public static AreaTagSummary from(AreaTag tag) {
        return new AreaTagSummary(tag.getId(), tag.getName(), tag.getSlug());
    }
}
