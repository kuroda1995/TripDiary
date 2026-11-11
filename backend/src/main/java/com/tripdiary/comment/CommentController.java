package com.tripdiary.comment;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.comment.dto.CommentPageResponse;
import com.tripdiary.comment.dto.CommentRequest;
import com.tripdiary.comment.dto.CommentResponse;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
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
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** コメント API（docs/features/05_comment.md 4章）。 */
@Tag(name = "コメント")
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "コメント一覧", description = "古い順（カーソルページング）。他人の非公開スポットは 404。")
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/spots/{id}/comments")
    public CommentPageResponse list(
            @PathVariable long id,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return commentService.list(id, viewer == null ? null : viewer.id(), cursor, limit);
    }

    @Operation(summary = "コメント投稿")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "投稿成功")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/spots/{id}/comments")
    public ResponseEntity<CommentResponse> create(
            @PathVariable long id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        CommentResponse created = commentService.create(id, me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id())).body(created);
    }

    @Operation(summary = "コメント編集", description = "コメントの投稿者本人のみ。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN（他人のコメント）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "COMMENT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/api/v1/comments/{commentId}")
    public CommentResponse update(
            @PathVariable long commentId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return commentService.update(commentId, me.id(), request);
    }

    @Operation(summary = "コメント削除", description = "コメントの投稿者本人、またはスポットの投稿者。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "COMMENT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/comments/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable long commentId, @AuthenticationPrincipal AuthenticatedUser me) {
        commentService.delete(commentId, me.id());
        return ResponseEntity.noContent().build();
    }
}
