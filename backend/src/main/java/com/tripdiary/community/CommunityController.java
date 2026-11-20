package com.tripdiary.community;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.community.dto.CommunityPageResponse;
import com.tripdiary.community.dto.CommunityRequest;
import com.tripdiary.community.dto.CommunityResponse;
import com.tripdiary.community.dto.MemberPageResponse;
import com.tripdiary.community.dto.MembershipResponse;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * コミュニティ API（docs/features/12_communities.md 4章）。
 *
 * <p>コミュニティ内のトピック（{@code /communities/{id}/topics}）は掲示板と同じ仕組みのため BoardController にある。
 */
@Tag(name = "コミュニティ")
@RestController
@RequiredArgsConstructor
public class CommunityController {

    static final int MAX_KEYWORD_LENGTH = 100;

    private final CommunityService communityService;

    @Operation(
            summary = "コミュニティ一覧・検索",
            description = "キーワード（名前・説明文の部分一致）とカテゴリで絞り込み、新着順か人気順（メンバー数順）に並べる。未ログインでも閲覧できる。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/communities")
    public CommunityPageResponse search(
            @Parameter(description = "キーワード（名前・説明文の部分一致、最大100文字）")
                    @RequestParam(required = false)
                    @Size(max = MAX_KEYWORD_LENGTH, message = "キーワードは{max}文字以内で入力してください")
                    String q,
            @Parameter(description = "カテゴリ") @RequestParam(required = false)
                    CommunityCategory category,
            @Parameter(description = "並び順: newest（新着順）/ popular（メンバー数順）")
                    @RequestParam(defaultValue = "newest")
                    @Pattern(regexp = "newest|popular", message = "並び順は newest か popular を指定してください")
                    String sort,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return communityService.search(
                q, category, CommunitySort.of(sort), viewerId(viewer), cursor, limit);
    }

    @Operation(summary = "コミュニティ詳細", description = "ログイン中なら参加しているか（joined）も返す。")
    @ApiResponse(
            responseCode = "404",
            description = "COMMUNITY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/communities/{id}")
    public CommunityResponse get(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return communityService.get(id, viewerId(viewer));
    }

    @Operation(summary = "コミュニティ作成", description = "作成者はオーナーとして自動で参加する。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "作成成功（Location ヘッダに URL）")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "COMMUNITY_NAME_TAKEN（同じ名前がある）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/communities")
    public ResponseEntity<CommunityResponse> create(
            @Valid @RequestBody CommunityRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        CommunityResponse created = communityService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/communities/" + created.id()))
                .body(created);
    }

    @Operation(summary = "コミュニティ削除", description = "オーナーのみ。メンバー・トピック・返信もまとめて削除される。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN（オーナーではない）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "COMMUNITY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/communities/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        communityService.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "コミュニティに参加する", description = "冪等（すでに参加していてもメンバー数は増えない）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "COMMUNITY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/communities/{id}/membership")
    public MembershipResponse join(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return communityService.join(id, me.id());
    }

    @Operation(summary = "コミュニティから退出する", description = "冪等（参加していなくても成功する）。オーナーは退出できない。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "404",
            description = "COMMUNITY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "OWNER_CANNOT_LEAVE（オーナーは退出できない）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/communities/{id}/membership")
    public MembershipResponse leave(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        return communityService.leave(id, me.id());
    }

    @Operation(summary = "メンバー一覧", description = "参加した順（オーナーが先頭）。未ログインでも閲覧できる。")
    @ApiResponse(
            responseCode = "404",
            description = "COMMUNITY_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/communities/{id}/members")
    public MemberPageResponse listMembers(
            @PathVariable long id,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return communityService.listMembers(id, cursor, limit);
    }

    private static Long viewerId(AuthenticatedUser viewer) {
        return viewer == null ? null : viewer.id();
    }
}
