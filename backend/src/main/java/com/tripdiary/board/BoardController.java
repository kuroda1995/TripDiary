package com.tripdiary.board;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.board.dto.BoardReplyPageResponse;
import com.tripdiary.board.dto.BoardReplyRequest;
import com.tripdiary.board.dto.BoardReplyResponse;
import com.tripdiary.board.dto.BoardTopicPageResponse;
import com.tripdiary.board.dto.BoardTopicRequest;
import com.tripdiary.board.dto.BoardTopicResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** お寺コミュニティ広場（掲示板）API（docs/features/10_community_board.md 4章）。 */
@Tag(name = "コミュニティ広場")
@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @Operation(summary = "トピック一覧", description = "最後に書き込みがあった順（カーソルページング）。未ログインでも閲覧できる。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/board-topics")
    public BoardTopicPageResponse listTopics(
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return boardService.listTopics(cursor, limit);
    }

    @Operation(summary = "トピック詳細")
    @ApiResponse(
            responseCode = "404",
            description = "BOARD_TOPIC_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/board-topics/{id}")
    public BoardTopicResponse getTopic(@PathVariable long id) {
        return boardService.getTopic(id);
    }

    @Operation(summary = "トピック作成")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "作成成功（Location ヘッダに URL）")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/board-topics")
    public ResponseEntity<BoardTopicResponse> createTopic(
            @Valid @RequestBody BoardTopicRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        BoardTopicResponse created = boardService.createTopic(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/board-topics/" + created.id()))
                .body(created);
    }

    @Operation(summary = "トピック削除", description = "トピックの投稿者本人のみ。返信もまとめて削除される。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN（他人のトピック）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "BOARD_TOPIC_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/board-topics/{id}")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        boardService.deleteTopic(id, me.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "返信一覧", description = "古い順（カーソルページング）。未ログインでも閲覧できる。")
    @ApiResponse(
            responseCode = "404",
            description = "BOARD_TOPIC_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/board-topics/{id}/replies")
    public BoardReplyPageResponse listReplies(
            @PathVariable long id,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return boardService.listReplies(id, cursor, limit);
    }

    @Operation(summary = "返信する", description = "トピックが一覧の先頭に上がる。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "投稿成功")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "BOARD_TOPIC_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/board-topics/{id}/replies")
    public ResponseEntity<BoardReplyResponse> createReply(
            @PathVariable long id,
            @Valid @RequestBody BoardReplyRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        BoardReplyResponse created = boardService.createReply(id, me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/board-replies/" + created.id()))
                .body(created);
    }

    @Operation(summary = "返信の削除", description = "返信の投稿者本人、またはトピックの投稿者。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "BOARD_REPLY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/board-replies/{replyId}")
    public ResponseEntity<Void> deleteReply(
            @PathVariable long replyId, @AuthenticationPrincipal AuthenticatedUser me) {
        boardService.deleteReply(replyId, me.id());
        return ResponseEntity.noContent().build();
    }
}
