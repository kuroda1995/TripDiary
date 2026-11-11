package com.tripdiary.auth;

import com.tripdiary.auth.AuthService.AuthResult;
import com.tripdiary.auth.dto.AuthResponse;
import com.tripdiary.auth.dto.LoginRequest;
import com.tripdiary.auth.dto.SignupRequest;
import com.tripdiary.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 認証 API（docs/features/01_auth.md 4章）。 */
@Tag(name = "認証", description = "新規登録・ログイン・トークン更新・ログアウト")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenCookies cookies;
    private final AuthResponses authResponses;

    @Operation(summary = "新規登録", description = "登録と同時にログイン状態にする。リフレッシュトークンは Cookie で返す。")
    @ApiResponse(responseCode = "201", description = "登録成功")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "EMAIL_ALREADY_EXISTS / USERNAME_ALREADY_EXISTS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return respond(HttpStatus.CREATED, authService.signup(request));
    }

    @Operation(summary = "ログイン", description = "同一 IP から5分間に10回まで。")
    @ApiResponse(responseCode = "200", description = "ログイン成功")
    @ApiResponse(
            responseCode = "401",
            description = "INVALID_CREDENTIALS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "TOO_MANY_REQUESTS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimiter.check(httpRequest.getRemoteAddr());
        return respond(HttpStatus.OK, authService.login(request));
    }

    @Operation(
            summary = "トークン更新",
            description = "Cookie のリフレッシュトークンで新しいアクセストークンを発行する。リフレッシュトークンもローテーションする。")
    @ApiResponse(responseCode = "200", description = "更新成功")
    @ApiResponse(
            responseCode = "401",
            description = "INVALID_REFRESH_TOKEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        return respond(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @Operation(summary = "ログアウト", description = "リフレッシュトークンを失効させ、Cookie を削除する。何度呼んでも成功する。")
    @ApiResponse(responseCode = "204", description = "ログアウト成功")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> respond(HttpStatus status, AuthResult result) {
        return authResponses.of(status, result);
    }
}
