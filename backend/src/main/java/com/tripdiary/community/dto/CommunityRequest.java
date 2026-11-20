package com.tripdiary.community.dto;

import com.tripdiary.community.CommunityCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** コミュニティの作成リクエスト（docs/features/12_communities.md 5章）。 */
@Schema(description = "コミュニティの作成リクエスト")
public record CommunityRequest(
        @Schema(description = "コミュニティ名（1〜50文字。前後の空白は除く）", example = "北陸・富山のお寺巡り同好会")
                @NotBlank(message = "コミュニティ名を入力してください")
                @Size(max = 50, message = "コミュニティ名は50文字以内で入力してください")
                String name,
        @Schema(
                        description = "説明文（テーマ・目的。1〜1000文字。前後の空白は除く）",
                        example = "瑞龍寺や勝興寺など、北陸のお寺の情報を交換しましょう。")
                @NotBlank(message = "説明文を入力してください")
                @Size(max = 1000, message = "説明文は1000文字以内で入力してください")
                String description,
        @Schema(
                        description = "カテゴリタグ（1つ以上。重複は1つにまとめる）",
                        example = "[\"REGIONAL\", \"BEGINNER_FRIENDLY\"]")
                @NotEmpty(message = "カテゴリを1つ以上選んでください")
                @Size(max = 5, message = "カテゴリは5つまで選べます")
                List<@NotNull CommunityCategory> categories) {}
