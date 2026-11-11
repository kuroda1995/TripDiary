package com.tripdiary.comment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** コメントの投稿・編集リクエスト（docs/features/05_comment.md 5章）。 */
@Schema(description = "コメントの投稿・編集リクエスト")
public record CommentRequest(
        @Schema(description = "本文（1〜1000文字。前後の空白は除く）", example = "何時ごろ行きましたか？")
                @NotBlank(message = "コメントを入力してください")
                @Size(max = 1000, message = "コメントは1000文字以内で入力してください")
                String body) {}
