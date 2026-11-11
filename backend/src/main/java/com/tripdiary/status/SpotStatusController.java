package com.tripdiary.status;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.spot.SpotStatus;
import com.tripdiary.status.dto.MyListPageResponse;
import com.tripdiary.status.dto.StatusRequest;
import com.tripdiary.status.dto.StatusResponse;
import com.tripdiary.status.dto.StatusSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ステータス管理 API（docs/features/08_spot_status.md 4章）。すべてログイン必須で、本人のリストだけを扱う。 */
@Tag(name = "ステータス（マイリスト）")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequiredArgsConstructor
public class SpotStatusController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final SpotStatusService service;

    @Operation(summary = "ステータスを設定・変更", description = "同じ値で設定し直しても成功し、日時は変わらない。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR（status の値が不正）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/spots/{id}/status")
    public StatusResponse set(
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return service.set(id, me.id(), request.status());
    }

    @Operation(summary = "ステータスを解除", description = "冪等（未設定でも 204）。")
    @ApiResponse(responseCode = "204", description = "解除した")
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND（存在しない・他人の非公開スポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/spots/{id}/status")
    public ResponseEntity<Void> clear(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        service.clear(id, me.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "マイリスト",
            description = "ステータスを更新した日時の新しい順（カーソルページング）。後から非公開になった他人の投稿は含めない。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/me/spot-statuses")
    public MyListPageResponse myList(
            @Parameter(description = "VISITED / WANT_TO_GO") @RequestParam SpotStatus status,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return service.myList(me.id(), status, cursor, limit);
    }

    @Operation(summary = "マイリストの件数")
    @GetMapping("/api/v1/me/spot-statuses/summary")
    public StatusSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.summary(me.id());
    }
}
