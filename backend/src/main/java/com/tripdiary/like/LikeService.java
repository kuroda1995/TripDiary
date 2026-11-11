package com.tripdiary.like;

import com.tripdiary.like.LikeRepository.LikedSpot;
import com.tripdiary.like.dto.LikeResponse;
import com.tripdiary.spot.Spot;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotRepository;
import com.tripdiary.spot.SpotService;
import com.tripdiary.spot.dto.SpotPageResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 行ってみたい！（docs/features/04_like.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final SpotService spotService;
    private final SpotRepository spotRepository;

    /** 付ける（冪等）。他人の非公開スポット・存在しないスポットは SPOT_NOT_FOUND。自分の投稿にも付けられる。 */
    @Transactional
    public LikeResponse like(long spotId, long userId) {
        spotService.getVisible(spotId, userId);
        likeRepository.insertIfAbsent(userId, spotId);
        log.info("Spot liked spotId={}", spotId);
        return new LikeResponse(spotId, true, likeRepository.count(spotId));
    }

    /** 外す（冪等）。 */
    @Transactional
    public LikeResponse unlike(long spotId, long userId) {
        spotService.getVisible(spotId, userId);
        likeRepository.delete(userId, spotId);
        log.info("Spot unliked spotId={}", spotId);
        return new LikeResponse(spotId, false, likeRepository.count(spotId));
    }

    /**
     * 自分が行ってみたい！したスポット（付けた日時の新しい順。カーソルページング）。
     *
     * <p>カーソルは「最後に返したスポットに付けた日時とスポット ID」。
     */
    @Transactional(readOnly = true)
    public SpotPageResponse myLikes(long userId, String cursor, int limit) {
        LikedSpot after = null;
        if (cursor != null) {
            SpotCursor decoded = SpotCursor.decode(cursor);
            after = new LikedSpot(decoded.id(), decoded.createdAt());
        }
        List<LikedSpot> liked = likeRepository.findLiked(userId, after, limit + 1);
        boolean hasNext = liked.size() > limit;
        List<LikedSpot> pageItems = hasNext ? liked.subList(0, limit) : liked;
        if (pageItems.isEmpty()) {
            return new SpotPageResponse(List.of(), null);
        }

        // ID で取得すると順番が崩れるため、付けた日時の順に並べ直す
        Map<Long, Spot> spots =
                spotRepository
                        .findAllWithUserByIdIn(pageItems.stream().map(LikedSpot::spotId).toList())
                        .stream()
                        .collect(Collectors.toMap(Spot::getId, Function.identity()));
        List<Spot> ordered =
                pageItems.stream().map(l -> spots.get(l.spotId())).filter(s -> s != null).toList();

        String nextCursor = null;
        if (hasNext) {
            LikedSpot last = pageItems.get(pageItems.size() - 1);
            nextCursor = new SpotCursor(last.likedAt(), last.spotId()).encode();
        }
        return new SpotPageResponse(spotService.toSummaries(ordered, userId), nextCursor);
    }
}
