package com.tripdiary.area;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AreaTagServiceTest {

    private final AreaTagRepository repository = mock(AreaTagRepository.class);
    private final AreaTagService service = new AreaTagService(repository);

    /** ID と親の ID だけを持つタグ（展開に使うのはこの2つだけ）。 */
    private static AreaTag tag(long id, Long parentId) {
        AreaTag tag = mock(AreaTag.class);
        when(tag.getId()).thenReturn(id);
        when(tag.getParentId()).thenReturn(parentId);
        return tag;
    }

    @BeforeEach
    void setUp() {
        // 関西(1) ─ 京都(11)・大阪(12) ─ 大阪市(121)、九州(2) ─ 福岡(21)
        List<AreaTag> all =
                List.of(
                        tag(1, null),
                        tag(11, 1L),
                        tag(12, 1L),
                        tag(121, 12L),
                        tag(2, null),
                        tag(21, 2L));
        when(repository.findAll()).thenReturn(all);
    }

    @Test
    @DisplayName("親タグを指定すると子孫タグ（孫まで）を含めて返す")
    void expandsDescendants() {
        assertThat(service.expandWithDescendants(List.of(1L)))
                .containsExactlyInAnyOrder(1L, 11L, 12L, 121L);
    }

    @Test
    @DisplayName("子タグだけならそのまま、複数・重複指定もまとめて返す")
    void leafAndMultiple() {
        assertThat(service.expandWithDescendants(List.of(11L))).containsExactly(11L);
        assertThat(service.expandWithDescendants(List.of(12L, 21L, 121L, 12L)))
                .containsExactlyInAnyOrder(12L, 121L, 21L);
    }

    @Test
    @DisplayName("存在しないタグが含まれると AREA_TAG_NOT_FOUND")
    void unknownTag() {
        assertThatThrownBy(() -> service.expandWithDescendants(List.of(1L, 999L)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(AreaTagService.AREA_TAG_NOT_FOUND));
    }
}
