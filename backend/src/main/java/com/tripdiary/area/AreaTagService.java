package com.tripdiary.area;

import com.tripdiary.area.dto.AreaTagResponse;
import com.tripdiary.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** エリアタグ（マスタ）の参照。 */
@Service
@RequiredArgsConstructor
public class AreaTagService {

    static final String AREA_TAG_NOT_FOUND = "AREA_TAG_NOT_FOUND";

    private final AreaTagRepository repository;

    /** 全タグを階層構造で返す（親 → 子の順、各階層は表示順）。 */
    @Transactional(readOnly = true)
    public List<AreaTagResponse> listTree() {
        List<AreaTag> all = repository.findAllByOrderByLevelAscSortOrderAscIdAsc();
        Map<Long, List<AreaTag>> childrenByParent = new LinkedHashMap<>();
        List<AreaTag> roots = new ArrayList<>();
        for (AreaTag tag : all) {
            if (tag.getParentId() == null) {
                roots.add(tag);
            } else {
                childrenByParent
                        .computeIfAbsent(tag.getParentId(), k -> new ArrayList<>())
                        .add(tag);
            }
        }
        return roots.stream().map(root -> toResponse(root, childrenByParent)).toList();
    }

    private static AreaTagResponse toResponse(AreaTag tag, Map<Long, List<AreaTag>> children) {
        return new AreaTagResponse(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                tag.getLevel(),
                children.getOrDefault(tag.getId(), List.of()).stream()
                        .map(child -> toResponse(child, children))
                        .toList());
    }

    /**
     * 指定した ID のタグをすべて取得する。
     *
     * @throws BadRequestException 存在しない ID が含まれる場合（AREA_TAG_NOT_FOUND）
     */
    @Transactional(readOnly = true)
    public Set<AreaTag> getAllByIds(Collection<Long> ids) {
        Set<Long> requested = new HashSet<>(ids);
        List<AreaTag> found = repository.findAllById(requested);
        if (found.size() != requested.size()) {
            throw new BadRequestException(AREA_TAG_NOT_FOUND, "存在しないエリアタグが指定されています");
        }
        return new HashSet<>(found);
    }
}
