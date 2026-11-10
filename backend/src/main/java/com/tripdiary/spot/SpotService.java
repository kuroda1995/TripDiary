package com.tripdiary.spot;

import com.tripdiary.area.AreaTagService;
import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.spot.Spot.SpotContent;
import com.tripdiary.spot.dto.SpotPageResponse;
import com.tripdiary.spot.dto.SpotRequest;
import com.tripdiary.spot.dto.SpotResponse;
import com.tripdiary.spot.dto.SpotSummaryResponse;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** スポットの作成・取得・更新・削除（docs/features/02_spot.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotService {

    static final String SPOT_NOT_FOUND = "SPOT_NOT_FOUND";
    static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    static final String DUPLICATE_AREA_TAG = "DUPLICATE_AREA_TAG";
    static final String VISITED_ON_IN_FUTURE = "VISITED_ON_IN_FUTURE";

    /** 「未来日」の判定は利用者の暮らす日本時間の日付で行う。 */
    static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private final SpotRepository spotRepository;
    private final UserRepository userRepository;
    private final AreaTagService areaTagService;
    private final Clock clock;

    @Transactional
    public SpotResponse create(long userId, SpotRequest request) {
        User author =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND, "ユーザーが存在しません"));
        Spot spot = spotRepository.save(new Spot(author, toContent(request)));
        log.info("Spot created spotId={}", spot.getId());
        return SpotResponse.from(spot);
    }

    /**
     * スポット詳細を返す。他人の非公開スポットは存在を知られないよう 404 にする。
     *
     * @param viewerId 閲覧者（未ログインなら null）
     */
    @Transactional(readOnly = true)
    public SpotResponse get(long spotId, Long viewerId) {
        return SpotResponse.from(findVisible(spotId, viewerId));
    }

    @Transactional
    public SpotResponse update(long spotId, long userId, SpotRequest request) {
        Spot spot = findEditable(spotId, userId);
        spot.update(toContent(request));
        log.info("Spot updated spotId={}", spotId);
        return SpotResponse.from(spot);
    }

    @Transactional
    public void delete(long spotId, long userId) {
        Spot spot = findEditable(spotId, userId);
        // 写真・いいね・コメント・ステータスは DB の ON DELETE CASCADE で削除される。
        // S3 の写真ファイルの削除は写真アップロード機能（PHOTO）で追加する。
        spotRepository.delete(spot);
        log.info("Spot deleted spotId={}", spotId);
    }

    /** 新着一覧（公開スポットのみ）。 */
    @Transactional(readOnly = true)
    public SpotPageResponse listPublic(String cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Spot> spots =
                cursor == null
                        ? spotRepository.findPublicFirstPage(page)
                        : withCursor(
                                cursor,
                                c -> spotRepository.findPublicAfter(c.createdAt(), c.id(), page));
        return toPage(spots, limit);
    }

    /** ユーザー別一覧。本人が見る場合は非公開スポットも含める。 */
    @Transactional(readOnly = true)
    public SpotPageResponse listByUser(String username, Long viewerId, String cursor, int limit) {
        User owner =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND, "ユーザーが存在しません"));
        boolean includePrivate = owner.getId().equals(viewerId);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Spot> spots =
                cursor == null
                        ? spotRepository.findByUserFirstPage(owner.getId(), includePrivate, page)
                        : withCursor(
                                cursor,
                                c ->
                                        spotRepository.findByUserAfter(
                                                owner.getId(),
                                                includePrivate,
                                                c.createdAt(),
                                                c.id(),
                                                page));
        return toPage(spots, limit);
    }

    private static List<Spot> withCursor(
            String cursor, java.util.function.Function<SpotCursor, List<Spot>> query) {
        return query.apply(SpotCursor.decode(cursor));
    }

    /** limit + 1 件取得し、超えた分があれば次ページありとしてカーソルを返す。 */
    private static SpotPageResponse toPage(List<Spot> spots, int limit) {
        boolean hasNext = spots.size() > limit;
        List<Spot> pageItems = hasNext ? spots.subList(0, limit) : spots;
        String nextCursor =
                hasNext ? SpotCursor.of(pageItems.get(pageItems.size() - 1)).encode() : null;
        return new SpotPageResponse(
                pageItems.stream().map(SpotSummaryResponse::from).toList(), nextCursor);
    }

    private Spot findVisible(long spotId, Long viewerId) {
        return spotRepository
                .findWithDetailsById(spotId)
                .filter(spot -> spot.isVisibleTo(viewerId))
                .orElseThrow(SpotService::spotNotFound);
    }

    /** 編集・削除できるスポット。他人の公開スポットは 403、他人の非公開スポットは 404。 */
    private Spot findEditable(long spotId, long userId) {
        Spot spot = findVisible(spotId, userId);
        if (!spot.isOwnedBy(userId)) {
            throw new ForbiddenException("他のユーザーのスポットは編集・削除できません");
        }
        return spot;
    }

    private SpotContent toContent(SpotRequest request) {
        if (new HashSet<>(request.areaTagIds()).size() != request.areaTagIds().size()) {
            throw new BadRequestException(DUPLICATE_AREA_TAG, "同じエリアタグが重複しています");
        }
        if (request.visitedOn() != null
                && request.visitedOn().isAfter(LocalDate.ofInstant(clock.instant(), JAPAN))) {
            throw new BadRequestException(VISITED_ON_IN_FUTURE, "訪問日に未来の日付は指定できません");
        }
        return new SpotContent(
                request.title().strip(),
                request.locationName().strip(),
                blankToNull(request.impression()),
                request.visitedOn(),
                request.visibility(),
                areaTagService.getAllByIds(request.areaTagIds()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static NotFoundException spotNotFound() {
        return new NotFoundException(SPOT_NOT_FOUND, "指定されたスポットは存在しません");
    }
}
