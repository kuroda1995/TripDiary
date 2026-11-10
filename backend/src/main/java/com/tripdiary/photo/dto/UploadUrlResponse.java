package com.tripdiary.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 署名付き URL（この URL へ画像を PUT する。Content-Type はリクエストと同じ値にする）。 */
@Schema(description = "署名付き URL")
public record UploadUrlResponse(
        @Schema(description = "PUT 先の署名付き URL（有効期限5分）") String uploadUrl,
        @Schema(
                        description = "スポット投稿時に photos[].s3Key に指定するキー",
                        example = "spots/42/9c1e4b7a-....jpg")
                String s3Key,
        @Schema(description = "URL の有効期限") Instant expiresAt) {}
