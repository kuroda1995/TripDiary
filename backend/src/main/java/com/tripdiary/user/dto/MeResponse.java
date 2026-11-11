package com.tripdiary.user.dto;

import com.tripdiary.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.UnaryOperator;

/** ログイン中のユーザー本人の情報（メールアドレスを含む）。 */
@Schema(description = "ログイン中のユーザー")
public record MeResponse(
        @Schema(example = "42") long id,
        @Schema(example = "taro@example.com") String email,
        @Schema(example = "taro_trip") String username,
        @Schema(example = "旅するタロウ") String displayName,
        @Schema(nullable = true, example = "週末は温泉めぐり♨") String bio,
        @Schema(description = "アイコン画像の URL（未設定は null）", nullable = true) String avatarUrl) {

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数
     */
    public static MeResponse from(User user, UnaryOperator<String> photoUrl) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                UserResponse.avatarUrl(user, photoUrl));
    }
}
