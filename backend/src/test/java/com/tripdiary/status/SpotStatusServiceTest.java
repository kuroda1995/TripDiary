package com.tripdiary.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotService;
import com.tripdiary.spot.SpotStatus;
import com.tripdiary.spot.dto.SpotSummaryResponse;
import com.tripdiary.status.SpotStatusRepository.StatusRow;
import com.tripdiary.status.dto.MyListPageResponse;
import com.tripdiary.support.MutableClock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotStatusServiceTest {

    private static final Instant T = Instant.parse("2026-11-01T00:00:00Z");

    private final SpotStatusRepository repository = mock(SpotStatusRepository.class);
    private final SpotService spotService = mock(SpotService.class);
    private final MutableClock clock = MutableClock.at("2026-11-11T03:00:00.123456789Z");
    private final SpotStatusService service = new SpotStatusService(repository, spotService, clock);

    private static SpotSummaryResponse summary(long id) {
        SpotSummaryResponse spot = mock(SpotSummaryResponse.class);
        when(spot.id()).thenReturn(id);
        return spot;
    }

    @Test
    @DisplayName("設定時は DB の精度（マイクロ秒）に切り捨てた現在時刻で UPSERT し、保存された値を返す")
    void setTruncatesToMicros() {
        Instant saved = Instant.parse("2026-11-11T03:00:00.123456Z");
        when(repository.find(2L, 10L))
                .thenReturn(Optional.of(new StatusRow(10L, SpotStatus.VISITED, saved, saved)));

        var response = service.set(10L, 2L, SpotStatus.VISITED);

        verify(spotService).getVisible(10L, 2L);
        verify(repository).upsert(2L, 10L, SpotStatus.VISITED, saved);
        assertThat(response.visitedAt()).isEqualTo(saved);
    }

    @Test
    @DisplayName("見えないスポットには設定・解除できず、DB には触れない")
    void notVisible() {
        when(spotService.getVisible(10L, 2L))
                .thenThrow(new NotFoundException("SPOT_NOT_FOUND", "x"));

        assertThatThrownBy(() -> service.set(10L, 2L, SpotStatus.VISITED))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.clear(10L, 2L)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).upsert(anyLong(), anyLong(), eq(SpotStatus.VISITED), eq(T));
        verify(repository, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("マイリストは limit + 1 件取得し、超えたら最後の行の更新日時とスポット ID をカーソルにする。取得できなかったスポットは飛ばす")
    void myListPaging() {
        when(repository.findByStatus(eq(2L), eq(SpotStatus.WANT_TO_GO), isNull(), eq(3)))
                .thenReturn(
                        List.of(
                                new StatusRow(30L, SpotStatus.WANT_TO_GO, null, T.plusSeconds(2)),
                                new StatusRow(20L, SpotStatus.WANT_TO_GO, null, T.plusSeconds(1)),
                                new StatusRow(10L, SpotStatus.WANT_TO_GO, null, T)));
        // 30 は取得までの間に削除された想定
        SpotSummaryResponse spot20 = summary(20L);
        when(spotService.summariesInOrder(List.of(30L, 20L), 2L)).thenReturn(List.of(spot20));

        MyListPageResponse page = service.myList(2L, SpotStatus.WANT_TO_GO, null, 2);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).spot()).isSameAs(spot20);
        assertThat(page.items().get(0).updatedAt()).isEqualTo(T.plusSeconds(1));
        assertThat(SpotCursor.decode(page.nextCursor()))
                .isEqualTo(new SpotCursor(T.plusSeconds(1), 20L));
    }

    @Test
    @DisplayName("件数はステータスごとに返す")
    void summaryCounts() {
        Map<SpotStatus, Long> counts = new EnumMap<>(SpotStatus.class);
        counts.put(SpotStatus.VISITED, 34L);
        counts.put(SpotStatus.WANT_TO_GO, 12L);
        when(repository.countByStatus(2L)).thenReturn(counts);

        var summary = service.summary(2L);

        assertThat(summary.visited()).isEqualTo(34);
        assertThat(summary.wantToGo()).isEqualTo(12);
    }
}
