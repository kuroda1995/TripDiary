package com.tripdiary.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** フォロー中一覧・フォロワー一覧（フォローした日時の新しい順）の1ページ。 */
@Schema(description = "フォロー一覧")
public record FollowUserPageResponse(
        List<FollowUserResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
