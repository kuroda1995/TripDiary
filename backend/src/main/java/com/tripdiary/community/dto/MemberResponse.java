package com.tripdiary.community.dto;

import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** コミュニティのメンバー1人。 */
@Schema(description = "コミュニティのメンバー")
public record MemberResponse(
        UserResponse user, @Schema(description = "作成者（オーナー）か") boolean owner, Instant joinedAt) {}
