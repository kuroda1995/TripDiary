package com.tripdiary.follow;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.follow.dto.FollowResponse;
import com.tripdiary.follow.dto.FollowUserPageResponse;
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

/** フォロー / フォロワー・タイムライン API（docs/features/06_follow.md 4章）。 */
@Tag(name = "フォロー")
@RestController
@RequiredArgsConstructor
public class FollowController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final FollowService followService;

    @Operation(summary = "フォローする", description = "冪等（すでにフォローしていても成功する）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "CANNOT_FOLLOW_SELF（自分自身）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/users/{username}/follow")
    public FollowResponse follow(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser me) {
        return followService.follow(username, me.id());
    }

    @Operation(summary = "フォロー解除", description = "冪等（フォローしていなくても成功する）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "CANNOT_FOLLOW_SELF（自分自身）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/users/{username}/follow")
    public FollowResponse unfollow(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser me) {
        return followService.unfollow(username, me.id());
    }

    @Operation(summary = "フォロー中一覧", description = "フォローした日時の新しい順（カーソルページング）。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/users/{username}/following")
    public FollowUserPageResponse following(
            @PathVariable String username,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return followService.following(username, viewerId(viewer), cursor, limit);
    }

    @Operation(summary = "フォロワー一覧", description = "フォローされた日時の新しい順（カーソルページング）。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/users/{username}/followers")
    public FollowUserPageResponse followers(
            @PathVariable String username,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return followService.followers(username, viewerId(viewer), cursor, limit);
    }

    @Operation(summary = "タイムライン", description = "フォロー中ユーザーの公開スポットを新着順に返す（自分の投稿は含めない）。形式は新着一覧と同じ。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/timeline")
    public SpotPageResponse timeline(
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return followService.timeline(me.id(), cursor, limit);
    }

    private static Long viewerId(AuthenticatedUser viewer) {
        return viewer == null ? null : viewer.id();
    }
}
