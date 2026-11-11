package com.tripdiary.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** フォローした・解除した後の状態。 */
@Schema(description = "フォローの状態")
public record FollowResponse(
        @Schema(example = "taro_trip") String username,
        @Schema(description = "自分がフォローしているか") boolean following,
        @Schema(description = "相手のフォロワー数", example = "121") long followerCount) {}
