package com.tripdiary.user.dto;

import com.tripdiary.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** ログイン中のユーザー本人の情報（メールアドレスを含む）。 */
@Schema(description = "ログイン中のユーザー")
public record MeResponse(
        @Schema(example = "42") long id,
        @Schema(example = "taro@example.com") String email,
        @Schema(example = "taro_trip") String username,
        @Schema(example = "旅するタロウ") String displayName,
        @Schema(nullable = true) String bio,
        @Schema(nullable = true) String avatarUrl) {

    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                null);
    }
}
