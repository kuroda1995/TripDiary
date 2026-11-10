package com.tripdiary.photo;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.photo.dto.UploadUrlRequest;
import com.tripdiary.photo.dto.UploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 写真アップロード API（docs/features/03_photo_upload.md 5章）。 */
@Tag(name = "写真アップロード")
@RestController
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @Operation(
            summary = "署名付き URL 発行",
            description = "ブラウザから S3 へ画像を直接 PUT するための URL を発行する。有効期限は5分。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / UNSUPPORTED_CONTENT_TYPE / FILE_TOO_LARGE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "500",
            description = "STORAGE_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/uploads/presigned-url")
    public UploadUrlResponse issueUploadUrl(
            @Valid @RequestBody UploadUrlRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return uploadService.issueUploadUrl(me.id(), request);
    }
}
