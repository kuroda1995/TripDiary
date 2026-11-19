package com.tripdiary.spot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * お寺の5段階評価（docs/features/09_temple_review.md 5章）。各項目 1〜5、未評価は null。
 *
 * <p>リクエスト・レスポンスで共通に使う。
 */
@Schema(description = "お寺の5段階評価（各項目 1〜5。未評価の項目は null）")
public record TempleRatings(
        @Schema(description = "静寂さ・雰囲気", example = "5", nullable = true)
                @Min(value = 1, message = "評価は1〜5で指定してください")
                @Max(value = 5, message = "評価は1〜5で指定してください")
                Integer tranquility,
        @Schema(description = "庭園・建築の美しさ", example = "4", nullable = true)
                @Min(value = 1, message = "評価は1〜5で指定してください")
                @Max(value = 5, message = "評価は1〜5で指定してください")
                Integer beauty,
        @Schema(description = "御朱印・参拝の充実度", example = "4", nullable = true)
                @Min(value = 1, message = "評価は1〜5で指定してください")
                @Max(value = 5, message = "評価は1〜5で指定してください")
                Integer worship,
        @Schema(description = "アクセス・駐車場の停めやすさ", example = "3", nullable = true)
                @Min(value = 1, message = "評価は1〜5で指定してください")
                @Max(value = 5, message = "評価は1〜5で指定してください")
                Integer access) {

    /** 1項目でも評価されているか。 */
    public boolean hasAny() {
        return tranquility != null || beauty != null || worship != null || access != null;
    }

    /** すべて未評価なら null（レスポンスで「評価なし」を null で表すため）。 */
    public static TempleRatings ofNullable(
            Integer tranquility, Integer beauty, Integer worship, Integer access) {
        TempleRatings ratings = new TempleRatings(tranquility, beauty, worship, access);
        return ratings.hasAny() ? ratings : null;
    }
}
