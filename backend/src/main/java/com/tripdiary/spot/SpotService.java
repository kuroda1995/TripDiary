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
import java.util.Set;
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
        return toResponse(spot);
    }

    /**
     * スポット詳細を返す。他人の非公開スポットは存在を知られないよう 404 にする。
     *
     * @param viewerId 閲覧者（未ログインなら null）
     */
    @Transactional(readOnly = true)
    public SpotResponse get(long spotId, Long viewerId) {
        return toResponse(findVisible(spotId, viewerId));
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
        return toResponse(spot);
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
    private SpotPageResponse toPage(List<Spot> spots, int limit) {
        boolean hasNext = spots.size() > limit;
        List<Spot> pageItems = hasNext ? spots.subList(0, limit) : spots;
        String nextCursor =
                hasNext ? SpotCursor.of(pageItems.get(pageItems.size() - 1)).encode() : null;
        return new SpotPageResponse(
                pageItems.stream()
                        .map(spot -> SpotSummaryResponse.from(spot, photoStorage::publicUrl))
                        .toList(),
                nextCursor);
    }

    private SpotResponse toResponse(Spot spot) {
        return SpotResponse.from(spot, photoStorage::publicUrl);
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
