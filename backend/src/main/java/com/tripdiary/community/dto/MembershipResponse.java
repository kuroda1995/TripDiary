package com.tripdiary.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 参加・退出の結果。 */
@Schema(description = "コミュニティへの参加状態")
public record MembershipResponse(
        @Schema(example = "3") long communityId,
        @Schema(description = "閲覧者が参加しているか") boolean joined,
        @Schema(description = "参加メンバー数（オーナーを含む）", example = "5") long memberCount) {}
