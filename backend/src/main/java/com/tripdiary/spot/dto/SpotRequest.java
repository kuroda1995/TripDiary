package com.tripdiary.spot.dto;

import com.tripdiary.spot.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** スポットの作成・更新リクエスト（docs/features/02_spot.md 5章）。写真は写真アップロード機能で追加する。 */
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
                Visibility visibility) {}
