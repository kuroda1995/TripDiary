package com.tripdiary.community.dto;

import com.tripdiary.board.dto.BoardTopicSummaryResponse;
import com.tripdiary.community.Community;
import com.tripdiary.community.CommunityCategory;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

/** 一覧用のコミュニティ（説明文は先頭のみ）。 */
@Schema(description = "一覧用のコミュニティ")
public record CommunitySummaryResponse(
        @Schema(example = "3") long id,
        @Schema(example = "北陸・富山のお寺巡り同好会") String name,
        @Schema(description = "説明文の先頭（最大120文字、省略時は末尾に…）") String descriptionExcerpt,
        List<CommunityCategory> categories,
        @Schema(description = "作成者（オーナー）") UserResponse owner,
        @Schema(description = "参加メンバー数（オーナーを含む）", example = "4") long memberCount,
        @Schema(description = "閲覧者が参加しているか（未ログインは false）") boolean joined,
        Instant createdAt) {

    public static CommunitySummaryResponse from(
            Community community, boolean joined, UnaryOperator<String> photoUrl) {
        return new CommunitySummaryResponse(
                community.getId(),
                community.getName(),
                BoardTopicSummaryResponse.excerpt(community.getDescription()),
                community.categories(),
                UserResponse.from(community.getOwner(), photoUrl),
                community.getMemberCount(),
                joined,
                community.getCreatedAt());
    }
}
