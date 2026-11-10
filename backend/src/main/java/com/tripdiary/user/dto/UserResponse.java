package com.tripdiary.user.dto;

import com.tripdiary.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

/** 他のユーザーにも見せてよいユーザー情報。 */
@Schema(description = "ユーザー（公開情報）")
public record UserResponse(
        @Schema(example = "42") long id,
        @Schema(example = "taro_trip") String username,
        @Schema(example = "旅するタロウ") String displayName,
        @Schema(description = "アイコン画像の URL（未設定は null）", nullable = true) String avatarUrl) {

    public static UserResponse from(User user) {
        // アイコン URL の組み立ては写真アップロード機能（PHOTO）で実装する
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), null);
    }
}
