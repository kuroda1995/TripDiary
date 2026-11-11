package com.tripdiary.status;

import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotService;
import com.tripdiary.spot.SpotStatus;
import com.tripdiary.spot.dto.SpotSummaryResponse;
import com.tripdiary.status.SpotStatusRepository.Position;
import com.tripdiary.status.SpotStatusRepository.StatusRow;
import com.tripdiary.status.dto.MyListItemResponse;
import com.tripdiary.status.dto.MyListPageResponse;
import com.tripdiary.status.dto.StatusResponse;
import com.tripdiary.status.dto.StatusSummaryResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スポットのステータス管理（docs/features/08_spot_status.md）。リストは本人だけが見られる。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotStatusService {

    private final SpotStatusRepository repository;
    private final SpotService spotService;
    private final Clock clock;

    /** 設定・変更する。他人の非公開スポット・存在しないスポットは SPOT_NOT_FOUND。同じ値で設定し直しても日時は変えない。 */
    @Transactional
    public StatusResponse set(long spotId, long userId, SpotStatus status) {
        spotService.getVisible(spotId, userId);
        // DB の精度（マイクロ秒）に合わせて切り捨て、返す値と保存される値をそろえる
        repository.upsert(userId, spotId, status, clock.instant().truncatedTo(ChronoUnit.MICROS));
        log.info("Spot status set spotId={} status={}", spotId, status);
        return StatusResponse.from(repository.find(userId, spotId).orElseThrow());
    }

    /** 解除する（冪等）。 */
    @Transactional
    public void clear(long spotId, long userId) {
        spotService.getVisible(spotId, userId);
        repository.delete(userId, spotId);
        log.info("Spot status cleared spotId={}", spotId);
    }

    /** マイリスト（ステータスを更新した日時の新しい順。カーソルは更新日時とスポット ID）。 */
    @Transactional(readOnly = true)
    public MyListPageResponse myList(long userId, SpotStatus status, String cursor, int limit) {
        Position after = null;
        if (cursor != null) {
            SpotCursor decoded = SpotCursor.decode(cursor);
            after = new Position(decoded.createdAt(), decoded.id());
        }
        List<StatusRow> rows = repository.findByStatus(userId, status, after, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<StatusRow> pageItems = hasNext ? rows.subList(0, limit) : rows;

        Map<Long, SpotSummaryResponse> spots =
                spotService
                        .summariesInOrder(
                                pageItems.stream().map(StatusRow::spotId).toList(), userId)
                        .stream()
                        .collect(Collectors.toMap(SpotSummaryResponse::id, Function.identity()));
        List<MyListItemResponse> items =
                pageItems.stream()
                        .filter(row -> spots.containsKey(row.spotId()))
                        .map(
                                row ->
                                        new MyListItemResponse(
                                                row.status(),
                                                row.visitedAt(),
                                                row.updatedAt(),
                                                spots.get(row.spotId())))
                        .toList();

        String nextCursor = null;
        if (hasNext) {
            StatusRow last = pageItems.get(pageItems.size() - 1);
            nextCursor = new SpotCursor(last.updatedAt(), last.spotId()).encode();
        }
        return new MyListPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public StatusSummaryResponse summary(long userId) {
        Map<SpotStatus, Long> counts = repository.countByStatus(userId);
        return new StatusSummaryResponse(
                counts.get(SpotStatus.VISITED), counts.get(SpotStatus.WANT_TO_GO));
    }
}
