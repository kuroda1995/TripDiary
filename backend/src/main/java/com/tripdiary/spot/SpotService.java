package com.tripdiary.spot;

import com.tripdiary.area.AreaTagService;
import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.photo.PhotoFilesDiscardedEvent;
import com.tripdiary.photo.PhotoFormat;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.photo.PhotoStorage.StoredObject;
import com.tripdiary.photo.UploadPurpose;
import com.tripdiary.spot.Spot.NewPhoto;
import com.tripdiary.spot.Spot.SpotContent;
import com.tripdiary.spot.SpotSearchRepository.Criteria;
import com.tripdiary.spot.SpotSearchRepository.Hit;
import com.tripdiary.spot.dto.PhotoRequest;
import com.tripdiary.spot.dto.SpotPageResponse;
import com.tripdiary.spot.dto.SpotRequest;
import com.tripdiary.spot.dto.SpotResponse;
import com.tripdiary.spot.dto.SpotSummaryResponse;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    static final String TOO_MANY_PHOTOS = "TOO_MANY_PHOTOS";
    static final String DUPLICATE_PHOTO_ORDER = "DUPLICATE_PHOTO_ORDER";
    static final String INVALID_PHOTO_KEY = "INVALID_PHOTO_KEY";

    static final int MAX_PHOTOS = 10;

    /** 「未来日」の判定は利用者の暮らす日本時間の日付で行う。 */
    static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final UserRepository userRepository;
    private final AreaTagService areaTagService;
    private final PhotoStorage photoStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final SpotReactionReader reactionReader;
    private final SpotSearchRepository spotSearchRepository;
    private final Clock clock;

    @Transactional
    public SpotResponse create(long userId, SpotRequest request) {
        User author =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND, "ユーザーが存在しません"));
        SpotContent content = toContent(request);
        List<NewPhoto> photos = resolvePhotos(userId, Set.of(), request.photos());
        Spot spot = new Spot(author, content);
        spot.replacePhotos(photos);
        spot = spotRepository.save(spot);
        log.info("Spot created spotId={} photos={}", spot.getId(), photos.size());
        return toResponse(spot, SpotReactions.NONE);
    }

    /**
     * スポット詳細を返す。他人の非公開スポットは存在を知られないよう 404 にする。
     *
     * @param viewerId 閲覧者（未ログインなら null）
     */
    @Transactional(readOnly = true)
    public SpotResponse get(long spotId, Long viewerId) {
        Spot spot = findVisible(spotId, viewerId);
        return toResponse(spot, reactionsOf(spot, viewerId));
    }

    @Transactional
    public SpotResponse update(long spotId, long userId, SpotRequest request) {
        Spot spot = findEditable(spotId, userId);
        SpotContent content = toContent(request);
        List<NewPhoto> photos = resolvePhotos(userId, spot.photoKeys(), request.photos());
        spot.update(content);
        List<String> removed = spot.replacePhotos(photos);
        discardFiles(removed);
        log.info(
                "Spot updated spotId={} photos={} removed={}",
                spotId,
                photos.size(),
                removed.size());
        return toResponse(spot, reactionsOf(spot, userId));
    }

    @Transactional
    public void delete(long spotId, long userId) {
        Spot spot = findEditable(spotId, userId);
        List<String> photoKeys = List.copyOf(spot.photoKeys());
        // 写真・いいね・コメント・ステータスの行は DB の ON DELETE CASCADE で削除される。
        // S3 の写真ファイルはコミット後に非同期で削除する（ロールバック時は残す）
        spotRepository.delete(spot);
        discardFiles(photoKeys);
        log.info("Spot deleted spotId={} photos={}", spotId, photoKeys.size());
    }

    /**
     * 新着一覧（公開スポットのみ）。
     *
     * @param viewerId 閲覧者（未ログインなら null。行ってみたい！を付けたかの判定に使う）
     */
    @Transactional(readOnly = true)
    public SpotPageResponse listPublic(Long viewerId, String cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Spot> spots =
                cursor == null
                        ? spotRepository.findPublicFirstPage(page)
                        : withCursor(
                                cursor,
                                c -> spotRepository.findPublicAfter(c.createdAt(), c.id(), page));
        return toPage(spots, limit, viewerId);
    }

    /**
     * 公開スポットの検索（docs/features/07_area_search.md）。エリアタグ（子孫タグを含む・OR）とキーワードは AND 条件。
     *
     * <p>条件なしの新着順は {@link #listPublic} と同じ結果になる。
     *
     * @param areaTagIds 指定されたエリアタグ（空なら絞り込まない）
     * @param keyword キーワード（null・空白のみなら絞り込まない）
     */
    @Transactional(readOnly = true)
    public SpotPageResponse search(
            List<Long> areaTagIds,
            String keyword,
            SpotSort sort,
            Long viewerId,
            String cursor,
            int limit) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (areaTagIds.isEmpty() && trimmed == null && sort == SpotSort.NEWEST) {
            return listPublic(viewerId, cursor, limit);
        }
        Set<Long> tagIds =
                areaTagIds.isEmpty() ? Set.of() : areaTagService.expandWithDescendants(areaTagIds);
        Hit after = cursor == null ? null : decodeCursor(cursor, sort);

        List<Hit> hits =
                spotSearchRepository.search(new Criteria(tagIds, trimmed, sort), after, limit + 1);
        boolean hasNext = hits.size() > limit;
        List<Hit> pageItems = hasNext ? hits.subList(0, limit) : hits;
        String nextCursor =
                hasNext ? encodeCursor(pageItems.get(pageItems.size() - 1), sort) : null;
        return new SpotPageResponse(
                summariesInOrder(pageItems.stream().map(Hit::id).toList(), viewerId), nextCursor);
    }

    private static Hit decodeCursor(String cursor, SpotSort sort) {
        if (sort == SpotSort.POPULAR) {
            PopularSpotCursor c = PopularSpotCursor.decode(cursor);
            return new Hit(c.id(), c.createdAt(), c.likeCount());
        }
        SpotCursor c = SpotCursor.decode(cursor);
        return new Hit(c.id(), c.createdAt(), 0);
    }

    private static String encodeCursor(Hit last, SpotSort sort) {
        return sort == SpotSort.POPULAR
                ? new PopularSpotCursor(last.likeCount(), last.createdAt(), last.id()).encode()
                : new SpotCursor(last.createdAt(), last.id()).encode();
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
        return toPage(spots, limit, viewerId);
    }

    private static List<Spot> withCursor(
            String cursor, java.util.function.Function<SpotCursor, List<Spot>> query) {
        return query.apply(SpotCursor.decode(cursor));
    }

    /** limit + 1 件取得し、超えた分があれば次ページありとしてカーソルを返す。 */
    private SpotPageResponse toPage(List<Spot> spots, int limit, Long viewerId) {
        boolean hasNext = spots.size() > limit;
        List<Spot> pageItems = hasNext ? spots.subList(0, limit) : spots;
        String nextCursor =
                hasNext ? SpotCursor.of(pageItems.get(pageItems.size() - 1)).encode() : null;
        return new SpotPageResponse(toSummaries(pageItems, viewerId), nextCursor);
    }

    /**
     * 一覧用の形に変換する。行ってみたい！の件数などは、件数に比例しない回数（1回）でまとめて取得する。
     *
     * <p>他の一覧（行ってみたい！した投稿など）からも使う。
     */
    public List<SpotSummaryResponse> toSummaries(List<Spot> spots, Long viewerId) {
        if (spots.isEmpty()) {
            return List.of();
        }
        Map<Long, SpotReactions> reactions =
                reactionReader.read(spots.stream().map(Spot::getId).toList(), viewerId);
        return spots.stream()
                .map(
                        spot ->
                                SpotSummaryResponse.from(
                                        spot,
                                        photoStorage::publicUrl,
                                        reactions.getOrDefault(spot.getId(), SpotReactions.NONE)))
                .toList();
    }

    /**
     * 指定した ID のスポットを、その並び順のまま一覧用の形にする（行ってみたい！した投稿・タイムラインなど）。
     *
     * <p>取得までの間に削除されたスポットは飛ばす。
     */
    @Transactional(readOnly = true)
    public List<SpotSummaryResponse> summariesInOrder(List<Long> orderedIds, Long viewerId) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        // ID で取得すると順番が崩れるため、指定された順に並べ直す
        Map<Long, Spot> spots =
                spotRepository.findAllWithUserByIdIn(orderedIds).stream()
                        .collect(Collectors.toMap(Spot::getId, Function.identity()));
        List<Spot> ordered = orderedIds.stream().map(spots::get).filter(Objects::nonNull).toList();
        return toSummaries(ordered, viewerId);
    }

    private SpotReactions reactionsOf(Spot spot, Long viewerId) {
        return reactionReader
                .read(List.of(spot.getId()), viewerId)
                .getOrDefault(spot.getId(), SpotReactions.NONE);
    }

    private SpotResponse toResponse(Spot spot, SpotReactions reactions) {
        return SpotResponse.from(spot, photoStorage::publicUrl, reactions);
    }

    /**
     * 写真の指定を検証し、表示順に並べて返す（docs/features/03_photo_upload.md 6章）。
     *
     * <p>新しく付ける写真は「自分のキーであること」「他の投稿で使われていないこと」「S3 上に実在し、対応形式・サイズ内であること」を確認する。
     * すでにこのスポットに付いている写真（{@code currentKeys}）は確認済みのため並び順だけを変える。
     */
    private List<NewPhoto> resolvePhotos(
            long userId, Set<String> currentKeys, List<PhotoRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > MAX_PHOTOS) {
            throw new BadRequestException(
                    TOO_MANY_PHOTOS, "写真は1投稿あたり%d枚までです".formatted(MAX_PHOTOS));
        }
        if (requested.stream().map(PhotoRequest::sortOrder).distinct().count()
                != requested.size()) {
            throw new BadRequestException(DUPLICATE_PHOTO_ORDER, "写真の並び順が重複しています");
        }
        if (requested.stream().map(PhotoRequest::s3Key).distinct().count() != requested.size()) {
            throw invalidPhotoKey();
        }
        return requested.stream()
                .sorted(Comparator.comparingInt(PhotoRequest::sortOrder))
                .map(
                        photo ->
                                currentKeys.contains(photo.s3Key())
                                        ? new NewPhoto(photo.s3Key(), null)
                                        : verifyNewPhoto(userId, photo.s3Key()))
                .toList();
    }

    private NewPhoto verifyNewPhoto(long userId, String s3Key) {
        if (!UploadPurpose.SPOT_PHOTO.isOwnKey(s3Key, userId)
                || spotPhotoRepository.existsByS3Key(s3Key)) {
            throw invalidPhotoKey();
        }
        StoredObject stored = photoStorage.head(s3Key).orElseThrow(SpotService::invalidPhotoKey);
        // 本番は署名の条件で形式・サイズを強制しているが、念のため実物でも確認する
        PhotoFormat format =
                PhotoFormat.ofContentType(stored.contentType())
                        .orElseThrow(SpotService::invalidPhotoKey);
        if (stored.size() > UploadPurpose.SPOT_PHOTO.maxBytes()) {
            throw invalidPhotoKey();
        }
        return new NewPhoto(s3Key, format.contentType());
    }

    /** コミット後に S3 のファイルを削除する（{@link com.tripdiary.photo.PhotoFileCleanupListener}）。 */
    private void discardFiles(List<String> s3Keys) {
        if (!s3Keys.isEmpty()) {
            eventPublisher.publishEvent(new PhotoFilesDiscardedEvent(s3Keys));
        }
    }

    private static BadRequestException invalidPhotoKey() {
        return new BadRequestException(INVALID_PHOTO_KEY, "写真を確認できませんでした。もう一度アップロードしてください");
    }

    /**
     * 閲覧できるスポット（他人の非公開スポット・存在しないスポットは SPOT_NOT_FOUND）。
     *
     * <p>行ってみたい！など、スポットに対する操作の前提チェックに使う。
     */
    @Transactional(readOnly = true)
    public Spot getVisible(long spotId, Long viewerId) {
        return findVisible(spotId, viewerId);
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
