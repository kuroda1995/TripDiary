package com.tripdiary.spot.dto;

import com.tripdiary.spot.TempleFeature;
import com.tripdiary.spot.TempleRatings;
import com.tripdiary.spot.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** スポットの作成・更新リクエスト（docs/features/02_spot.md 5章）。 */
@Schema(description = "スポットの作成・更新リクエスト")
public record SpotRequest(
        @Schema(example = "朝の清水寺")
                @NotBlank(message = "タイトルを入力してください")
                @Size(max = 100, message = "タイトルは100文字以内で入力してください")
                String title,
        @Schema(example = "清水寺（京都府京都市東山区）")
                @NotBlank(message = "場所を入力してください")
                @Size(max = 200, message = "場所は200文字以内で入力してください")
                String locationName,
        @Schema(description = "エリアタグの ID（1〜5個）", example = "[29]")
                @NotNull(message = "エリアタグを選択してください")
                @Size(min = 1, max = 5, message = "エリアタグは1〜5個選択してください")
                List<@NotNull Long> areaTagIds,
        @Schema(example = "開門直後に行くと人が少なくて最高でした。", nullable = true)
                @Size(max = 5000, message = "感想は5000文字以内で入力してください")
                String impression,
        @Schema(description = "訪問日（未来日は不可）", example = "2026-10-03", nullable = true)
                LocalDate visitedOn,
        @Schema(description = "公開範囲", example = "PUBLIC") @NotNull(message = "公開範囲を選択してください")
                Visibility visibility,
        @Schema(description = "写真（0〜10枚）。更新時は送信した配列で置き換える（含まれない写真は削除）。省略時は写真なし", nullable = true)
                List<@Valid @NotNull PhotoRequest> photos,
        @Schema(description = "お寺の5段階評価（任意。省略・null は未評価）", nullable = true) @Valid
                TempleRatings ratings,
        @Schema(
                        description = "お寺の特徴タグ（任意。重複は1つにまとめる）。省略時はタグなし",
                        nullable = true,
                        example = "[\"GOSHUIN\",\"GARDEN\"]")
                @Size(max = 5, message = "特徴タグは5個まで選択できます")
                List<@NotNull TempleFeature> templeFeatures) {

    /** お寺の評価・特徴タグなしのリクエスト。 */
    public SpotRequest(
            String title,
            String locationName,
            List<Long> areaTagIds,
            String impression,
            LocalDate visitedOn,
            Visibility visibility,
            List<PhotoRequest> photos) {
        this(
                title,
                locationName,
                areaTagIds,
                impression,
                visitedOn,
                visibility,
                photos,
                null,
                null);
    }
}
