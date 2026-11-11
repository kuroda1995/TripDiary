package com.tripdiary.user.dto;

import com.tripdiary.user.JdbcUserStatsReader.UserStats;
import com.tripdiary.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.UnaryOperator;

/** ユーザーのプロフィール（誰でも閲覧できる情報と件数）。 */
@Schema(description = "ユーザーのプロフィール")
public record ProfileResponse(
        @Schema(example = "42") long id,
        @Schema(example = "taro_trip") String username,
        @Schema(example = "旅するタロウ") String displayName,
        @Schema(nullable = true, example = "週末は温泉めぐり♨") String bio,
        @Schema(description = "アイコン画像の URL（未設定は null）", nullable = true) String avatarUrl,
        @Schema(description = "投稿数（本人が見る場合は非公開も含む）", example = "28") long spotCount,
        @Schema(description = "フォロー数", example = "15") long followingCount,
        @Schema(description = "フォロワー数", example = "120") long followerCount,
        @Schema(description = "閲覧者がフォローしているか（未ログイン・本人は false）") boolean followedByMe) {

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数
     */
    public static ProfileResponse from(User user, UserStats stats, UnaryOperator<String> photoUrl) {
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                UserResponse.avatarUrl(user, photoUrl),
                stats.spotCount(),
                stats.followingCount(),
                stats.followerCount(),
                stats.followedByViewer());
    }
}
