package com.tripdiary.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** スポットに付ける写真（署名付き URL 発行時に受け取った s3Key と表示順）。 */
@Schema(description = "スポットの写真")
public record PhotoRequest(
        @Schema(example = "spots/42/9c1e4b7a-1f0e-4a8e-9a43-2d1c0f6b7e21.jpg")
                @NotBlank(message = "写真のキーを指定してください")
                @Size(max = 512)
                String s3Key,
        @Schema(description = "表示順（0〜9。0 がカバー写真）", example = "0")
                @NotNull(message = "写真の並び順を指定してください")
                @Min(value = 0, message = "写真の並び順は0〜9で指定してください")
                @Max(value = 9, message = "写真の並び順は0〜9で指定してください")
                Integer sortOrder) {}
