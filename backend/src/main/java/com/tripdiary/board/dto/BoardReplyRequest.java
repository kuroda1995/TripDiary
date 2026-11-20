package com.tripdiary.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 返信の投稿リクエスト（docs/features/10_community_board.md 5章）。 */
@Schema(description = "返信の投稿リクエスト")
public record BoardReplyRequest(
        @Schema(description = "本文（1〜1000文字。前後の空白は除く）", example = "東福寺の通天橋がおすすめです。")
                @NotBlank(message = "返信を入力してください")
                @Size(max = 1000, message = "返信は1000文字以内で入力してください")
                String body) {}
