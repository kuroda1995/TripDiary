package com.tripdiary.spot;

import com.tripdiary.auth.AuthenticatedUser;
import com.tripdiary.common.config.OpenApiConfig;
import com.tripdiary.common.exception.ErrorResponse;
import com.tripdiary.spot.dto.SpotPageResponse;
import com.tripdiary.spot.dto.SpotRequest;
import com.tripdiary.spot.dto.SpotResponse;
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
import java.util.List;
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

/** スポット API（docs/features/02_spot.md 4章）。 */
@Tag(name = "スポット")
@RestController
@RequiredArgsConstructor
public class SpotController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int MAX_AREA_IDS = 10;
    static final int MAX_KEYWORD_LENGTH = 100;

    private static final String SPOT_INPUT_ERRORS =
            "VALIDATION_ERROR / AREA_TAG_NOT_FOUND / DUPLICATE_AREA_TAG / VISITED_ON_IN_FUTURE"
                    + " / TOO_MANY_PHOTOS / DUPLICATE_PHOTO_ORDER / INVALID_PHOTO_KEY";

    private final SpotService spotService;

    @Operation(
            summary = "一覧・検索",
            description =
                    "公開スポットを返す（カーソルページング）。エリアタグ（子孫タグを含む・OR）とキーワードで絞り込み、"
                            + "新着順・人気順（行ってみたい！数順）に並べ替えられる。条件を省略すると新着一覧。"
                            + "カーソルは並び順ごとに形式が違うため、並び順を変えたら最初のページから取得し直す。")
    @ApiResponse(
            responseCode = "400",
            description = "VALIDATION_ERROR / INVALID_CURSOR / AREA_TAG_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/spots")
    public SpotPageResponse list(
            @Parameter(description = "エリアタグ ID（カンマ区切りで最大10個、OR 条件。親タグは子孫タグを含む）", example = "2,3")
                    @RequestParam(defaultValue = "")
                    @Size(max = MAX_AREA_IDS, message = "エリアタグは{max}個まで指定できます")
                    List<Long> areaIds,
            @Parameter(description = "キーワード（タイトル・場所名の部分一致、最大100文字）")
                    @RequestParam(required = false)
                    @Size(max = MAX_KEYWORD_LENGTH, message = "キーワードは{max}文字以内で入力してください")
                    String q,
            @Parameter(description = "並び順: newest（新着順）/ popular（行ってみたい！数順）")
                    @RequestParam(defaultValue = "newest")
                    @Pattern(regexp = "newest|popular", message = "並び順は newest か popular を指定してください")
                    String sort,
            @Parameter(description = "前のページの nextCursor") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "取得件数（1〜50）")
                    @RequestParam(defaultValue = "" + DEFAULT_LIMIT)
                    @Min(1)
                    @Max(MAX_LIMIT)
                    int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return spotService.search(areaIds, q, SpotSort.of(sort), viewerId(viewer), cursor, limit);
    }

    @Operation(summary = "ユーザー別一覧", description = "本人が見る場合は非公開スポットも含める。")
    @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/users/{username}/spots")
    public SpotPageResponse listByUser(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit,
            @AuthenticationPrincipal AuthenticatedUser viewer) {
        return spotService.listByUser(username, viewerId(viewer), cursor, limit);
    }

    @Operation(summary = "スポット詳細", description = "他人の非公開スポットは 404。")
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/v1/spots/{id}")
    public SpotResponse get(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return spotService.get(id, viewerId(viewer));
    }

    @Operation(summary = "スポット作成")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "作成成功（Location ヘッダに URL）")
    @ApiResponse(
            responseCode = "400",
            description = SPOT_INPUT_ERRORS,
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/v1/spots")
    public ResponseEntity<SpotResponse> create(
            @Valid @RequestBody SpotRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        SpotResponse created = spotService.create(me.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/spots/" + created.id())).body(created);
    }

    @Operation(summary = "スポット更新", description = "投稿者本人のみ。全項目を置き換える（含まれない写真は削除）。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(
            responseCode = "400",
            description = SPOT_INPUT_ERRORS,
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN（他人のスポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "SPOT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PutMapping("/api/v1/spots/{id}")
    public SpotResponse update(
            @PathVariable long id,
            @Valid @RequestBody SpotRequest request,
            @AuthenticationPrincipal AuthenticatedUser me) {
        return spotService.update(id, me.id(), request);
    }

    @Operation(summary = "スポット削除", description = "投稿者本人のみ。いいね・コメント・ステータスも削除される。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "削除成功")
    @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN（他人のスポット）",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/api/v1/spots/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable long id, @AuthenticationPrincipal AuthenticatedUser me) {
        spotService.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }

    private static Long viewerId(AuthenticatedUser viewer) {
        return viewer == null ? null : viewer.id();
    }
}
