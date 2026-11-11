package com.tripdiary.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** フォロー中一覧・フォロワー一覧の1行。 */
@Schema(description = "フォロー一覧のユーザー")
public record FollowUserResponse(
        @Schema(example = "7") long id,
        @Schema(example = "hanako") String username,
        @Schema(example = "はなこ") String displayName,
        @Schema(description = "アイコン画像の URL（未設定は null）", nullable = true) String avatarUrl,
        @Schema(description = "閲覧者がこのユーザーをフォローしているか（未ログインは false）") boolean followedByMe) {}
