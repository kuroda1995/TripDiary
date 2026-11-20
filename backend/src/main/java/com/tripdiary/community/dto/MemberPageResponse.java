package com.tripdiary.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** メンバー一覧（参加した順）のページ。 */
@Schema(description = "コミュニティのメンバー一覧（カーソルページング）")
public record MemberPageResponse(
        List<MemberResponse> items,
        @Schema(description = "次のページのカーソル（最後のページなら null）", nullable = true) String nextCursor) {}
