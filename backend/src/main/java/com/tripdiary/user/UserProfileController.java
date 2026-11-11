package com.tripdiary.user;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.user.dto.ProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザーのプロフィール API（docs/features/06_follow.md 4.1）。
 *
 * <p>{@code /api/v1/users/me} はログイン中のユーザー本人の API（{@link UserController}）が優先される。
 * ユーザー名は3文字以上のため「me」と衝突しない。
 */
@Tag(name = "ユーザー")
@RestController
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "プロフィール", description = "投稿数・フォロー数・フォロワー数と、自分がフォローしているかを返す。")
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/users/{username}")
    public ProfileResponse profile(
            @PathVariable String username, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return userProfileService.profile(username, viewer == null ? null : viewer.id());
    }
}
