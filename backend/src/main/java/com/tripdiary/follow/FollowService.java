package com.tripdiary.follow;

import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.follow.FollowRepository.FollowRow;
import com.tripdiary.follow.FollowRepository.Position;
import com.tripdiary.follow.FollowRepository.TimelineEntry;
import com.tripdiary.follow.dto.FollowResponse;
import com.tripdiary.follow.dto.FollowUserPageResponse;
import com.tripdiary.follow.dto.FollowUserResponse;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotService;
import com.tripdiary.spot.dto.SpotPageResponse;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** フォロー / フォロワーとタイムライン（docs/features/06_follow.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    static final String CANNOT_FOLLOW_SELF = "CANNOT_FOLLOW_SELF";

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final SpotService spotService;
    private final PhotoStorage photoStorage;

    /** フォローする（冪等）。自分自身は CANNOT_FOLLOW_SELF、存在しないユーザーは USER_NOT_FOUND。 */
    @Transactional
    public FollowResponse follow(String username, long followerId) {
        User target = findOther(username, followerId);
        followRepository.insertIfAbsent(followerId, target.getId());
        log.info("User followed followeeId={}", target.getId());
        return new FollowResponse(
                target.getUsername(), true, followRepository.countFollowers(target.getId()));
    }

    /** フォロー解除（冪等）。 */
    @Transactional
    public FollowResponse unfollow(String username, long followerId) {
        User target = findOther(username, followerId);
        followRepository.delete(followerId, target.getId());
        log.info("User unfollowed followeeId={}", target.getId());
        return new FollowResponse(
                target.getUsername(), false, followRepository.countFollowers(target.getId()));
    }

    /**
     * フォロー中一覧（フォローした日時の新しい順）。
     *
     * @param viewerId 閲覧者（未ログインなら null。各行の followedByMe の判定に使う）
     */
    @Transactional(readOnly = true)
    public FollowUserPageResponse following(
            String username, Long viewerId, String cursor, int limit) {
        User owner = find(username);
        return toPage(
                followRepository.findFollowing(owner.getId(), viewerId, decode(cursor), limit + 1),
                limit);
    }

    /** フォロワー一覧（フォローされた日時の新しい順）。 */
    @Transactional(readOnly = true)
    public FollowUserPageResponse followers(
            String username, Long viewerId, String cursor, int limit) {
        User owner = find(username);
        return toPage(
                followRepository.findFollowers(owner.getId(), viewerId, decode(cursor), limit + 1),
                limit);
    }

    /** タイムライン（フォロー中ユーザーの公開スポットを新着順）。形式はスポットの新着一覧と同じ。 */
    @Transactional(readOnly = true)
    public SpotPageResponse timeline(long userId, String cursor, int limit) {
        TimelineEntry after = null;
        if (cursor != null) {
            SpotCursor decoded = SpotCursor.decode(cursor);
            after = new TimelineEntry(decoded.id(), decoded.createdAt());
        }
        List<TimelineEntry> entries = followRepository.findTimeline(userId, after, limit + 1);
        boolean hasNext = entries.size() > limit;
        List<TimelineEntry> pageItems = hasNext ? entries.subList(0, limit) : entries;

        String nextCursor = null;
        if (hasNext) {
            TimelineEntry last = pageItems.get(pageItems.size() - 1);
            nextCursor = new SpotCursor(last.createdAt(), last.spotId()).encode();
        }
        return new SpotPageResponse(
                spotService.summariesInOrder(
                        pageItems.stream().map(TimelineEntry::spotId).toList(), userId),
                nextCursor);
    }

    /** limit + 1 件取得し、超えた分があれば次ページありとしてカーソルを返す。 */
    private FollowUserPageResponse toPage(List<FollowRow> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<FollowRow> pageItems = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasNext) {
            FollowRow last = pageItems.get(pageItems.size() - 1);
            nextCursor = new SpotCursor(last.followedAt(), last.userId()).encode();
        }
        return new FollowUserPageResponse(
                pageItems.stream().map(this::toResponse).toList(), nextCursor);
    }

    private FollowUserResponse toResponse(FollowRow row) {
        return new FollowUserResponse(
                row.userId(),
                row.username(),
                row.displayName(),
                row.avatarS3Key() == null ? null : photoStorage.publicUrl(row.avatarS3Key()),
                row.followedByViewer());
    }

    /** カーソルは「最後に返した行のフォロー日時と相手のユーザー ID」（形式はスポット一覧と共通）。 */
    private static Position decode(String cursor) {
        if (cursor == null) {
            return null;
        }
        SpotCursor decoded = SpotCursor.decode(cursor);
        return new Position(decoded.createdAt(), decoded.id());
    }

    private User findOther(String username, long me) {
        User target = find(username);
        if (target.getId() == me) {
            throw new BadRequestException(CANNOT_FOLLOW_SELF, "自分自身はフォローできません");
        }
        return target;
    }

    private User find(String username) {
        return userRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND, "ユーザーが存在しません"));
    }
}
