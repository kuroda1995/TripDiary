package com.tripdiary.area;

import com.tripdiary.area.dto.AreaTagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** エリアタグ API（docs/features/07_area_search.md 4.1）。 */
@Tag(name = "エリアタグ")
@RestController
@RequestMapping("/api/v1/areas")
@RequiredArgsConstructor
public class AreaTagController {

    private final AreaTagService areaTagService;

    @Operation(summary = "エリアタグ一覧（階層構造）", description = "ほとんど変わらないマスタデータのため1時間キャッシュできる。")
    @GetMapping
    public ResponseEntity<List<AreaTagResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(areaTagService.listTree());
    }
}
