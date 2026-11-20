package com.tripdiary.community.dto;

import com.tripdiary.community.Community;
import com.tripdiary.community.CommunityCategory;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

/** コミュニティ詳細。 */
@Schema(description = "コミュニティ")
public record CommunityResponse(
        @Schema(example = "3") long id,
        @Schema(example = "北陸・富山のお寺巡り同好会") String name,
        @Schema(description = "説明文（プレーンテキスト。改行を含む）") String description,
        List<CommunityCategory> categories,
        @Schema(description = "作成者（オーナー）") UserResponse owner,
        @Schema(description = "参加メンバー数（オーナーを含む）", example = "4") long memberCount,
        @Schema(description = "コミュニティ内のトピック数", example = "2") long topicCount,
        @Schema(description = "閲覧者が参加しているか（未ログインは false）") boolean joined,
        Instant createdAt) {

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数（オーナーのアイコン用）
     */
    public static CommunityResponse from(
            Community community,
            long memberCount,
            long topicCount,
            boolean joined,
            UnaryOperator<String> photoUrl) {
        return new CommunityResponse(
                community.getId(),
                community.getName(),
                community.getDescription(),
                community.categories(),
                UserResponse.from(community.getOwner(), photoUrl),
                memberCount,
                topicCount,
                joined,
                community.getCreatedAt());
    }
}
