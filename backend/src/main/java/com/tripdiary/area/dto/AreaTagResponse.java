package com.tripdiary.area.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** エリアタグ（階層構造）。 */
@Schema(description = "エリアタグ（子タグを含む階層構造）")
public record AreaTagResponse(
        @Schema(example = "4") long id,
        @Schema(example = "関西") String name,
        @Schema(example = "kansai") String slug,
        @Schema(description = "1: 地方・海外 / 2: 都道府県・国や地域", example = "1") int level,
        List<AreaTagResponse> children) {}
