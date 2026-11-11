package com.tripdiary.user;

import com.tripdiary.auth.AuthResponses;
import com.tripdiary.auth.AuthService;
import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.auth.LoginRateLimiter;
import com.tripdiary.auth.dto.AuthResponse;
import com.tripdiary.auth.dto.PasswordChangeRequest;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.user.dto.MeResponse;
import com.tripdiary.user.dto.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ログイン中のユーザーの API（docs/features/01_auth.md 4.5〜4.7）。 */
@Tag(name = "ユーザー")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final AuthResponses authResponses;
    private final LoginRateLimiter rateLimiter;

    @Operation(summary = "ログイン中のユーザー情報")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "200", description = "取得成功")
    @ApiResponse(
            responseCode = "401",
            description = "UNAUTHORIZED / TOKEN_EXPIRED / INVALID_TOKEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return userService.me(me.id());
    }

    @Operation(
            summary = "プロフィール更新",
            description = "送った項目だけを更新する。bio・avatarS3Key は null で削除。差し替え前のアイコン画像は削除される。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_AVATAR_KEY",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping
    public MeResponse updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return userService.updateProfile(me.id(), request);
    }

    @Operation(
            summary = "パスワード変更",
            description = "他の端末のログインを切るため全リフレッシュトークンを失効させ、この端末には新しいトークンを発行する。" + " 5分間に10回まで。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "200", description = "変更成功（新しいトークンを返す）")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURRENT_PASSWORD / PASSWORD_UNCHANGED",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "TOO_MANY_REQUESTS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        // アクセストークンを盗まれた場合に現在のパスワードを総当たりされないよう、試行回数を制限する
        rateLimiter.check("password-change:" + me.id(), "パスワード変更の試行回数が多すぎます。しばらく時間をおいてお試しください");
        return authResponses.of(HttpStatus.OK, authService.changePassword(me.id(), request));
    }
}
