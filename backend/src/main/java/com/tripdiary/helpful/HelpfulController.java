package com.tripdiary.helpful;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.helpful.dto.HelpfulResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** 参考になった API（docs/features/11_community_reactions.md 4章）。 */
@Tag(name = "参考になった")
@RestController
@RequiredArgsConstructor
public class HelpfulController {

    private final HelpfulService helpfulService;

    @Operation(summary = "参考になったを付ける", description = "冪等（すでに付いていても件数は増えない）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/spots/{id}/helpful")
    public HelpfulResponse mark(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return helpfulService.mark(id, me.id());
    }

    @Operation(summary = "参考になったを外す", description = "冪等（付いていなくても成功する）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/spots/{id}/helpful")
    public HelpfulResponse unmark(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return helpfulService.unmark(id, me.id());
    }
}
