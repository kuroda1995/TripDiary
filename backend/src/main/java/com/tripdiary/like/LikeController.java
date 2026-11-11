package com.tripdiary.like;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.like.dto.LikeResponse;
import com.tripdiary.spot.dto.SpotPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 行ってみたい！API（docs/features/04_like.md 4章）。 */
@Tag(name = "行ってみたい！")
@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @Operation(summary = "行ってみたい！を付ける", description = "冪等（すでに付いていても件数は増えない）。自分の投稿にも付けられる。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/spots/{id}/like")
    public LikeResponse like(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return likeService.like(id, me.id());
    }

    @Operation(summary = "行ってみたい！を外す", description = "冪等（付いていなくても成功する）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/spots/{id}/like")
    public LikeResponse unlike(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return likeService.unlike(id, me.id());
    }

    @Operation(summary = "行ってみたい！した投稿", description = "付けた日時の新しい順（カーソルページング）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/me/likes")
    public SpotPageResponse myLikes(
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return likeService.myLikes(me.id(), cursor, limit);
    }
}
