package com.tripdiary.auth.dto;

import com.tripdiary.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 認証成功時のレスポンス。リフレッシュトークンは本文には含めず、HttpOnly Cookie で返す。
 *
 * @param accessToken アクセストークン（JWT）。フロントエンドはメモリ上にのみ保持する
 * @param tokenType 常に {@code Bearer}
 * @param expiresIn アクセストークンの有効期間（秒）
 * @param user ログインしたユーザー
 */
@Schema(description = "認証成功レスポンス（リフレッシュトークンは HttpOnly Cookie で返す）")
public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn,
        MeResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, MeResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
