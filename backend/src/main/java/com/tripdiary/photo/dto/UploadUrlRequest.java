package com.tripdiary.photo.dto;

import com.tripdiary.photo.UploadPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 署名付き URL の発行リクエスト（docs/features/03_photo_upload.md 5.1）。 */
@Schema(description = "署名付き URL の発行リクエスト")
public record UploadUrlRequest(
        @Schema(description = "用途", example = "SPOT_PHOTO") @NotNull(message = "用途を指定してください")
                UploadPurpose purpose,
        @Schema(description = "画像の MIME タイプ", example = "image/jpeg")
                @NotBlank(message = "画像の形式を指定してください")
                String contentType,
        @Schema(description = "ファイルサイズ（バイト）", example = "2457600")
                @NotNull(message = "ファイルサイズを指定してください")
                @Positive(message = "空のファイルはアップロードできません")
                Long contentLength) {}
