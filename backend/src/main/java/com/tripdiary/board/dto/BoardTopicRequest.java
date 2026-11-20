package com.tripdiary.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** トピックの作成リクエスト（docs/features/10_community_board.md 5章）。 */
@Schema(description = "トピックの作成リクエスト")
public record BoardTopicRequest(
        @Schema(description = "タイトル（1〜100文字。前後の空白は除く）", example = "紅葉の時期におすすめのお寺を教えてください")
                @NotBlank(message = "タイトルを入力してください")
                @Size(max = 100, message = "タイトルは100文字以内で入力してください")
                String title,
        @Schema(description = "本文（1〜2000文字。前後の空白は除く）", example = "11月中旬に京都へ行きます。")
                @NotBlank(message = "本文を入力してください")
                @Size(max = 2000, message = "本文は2000文字以内で入力してください")
                String body) {}
