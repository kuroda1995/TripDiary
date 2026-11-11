package com.tripdiary.user.dto;

import com.tripdiary.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.UnaryOperator;

/** 他のユーザーにも見せてよいユーザー情報。 */
@Schema(description = "ユーザー（公開情報）")
public record UserResponse(
        @Schema(example = "42") long id,
        @Schema(example = "taro_trip") String username,
        @Schema(example = "旅するタロウ") String displayName,
        @Schema(description = "アイコン画像の URL（未設定は null）", nullable = true) String avatarUrl) {

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数
     */
    public static UserResponse from(User user, UnaryOperator<String> photoUrl) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl(user, photoUrl));
    }

    /** アイコンの配信 URL（未設定なら null）。 */
    public static String avatarUrl(User user, UnaryOperator<String> photoUrl) {
        return user.getAvatarS3Key() == null ? null : photoUrl.apply(user.getAvatarS3Key());
    }
}
