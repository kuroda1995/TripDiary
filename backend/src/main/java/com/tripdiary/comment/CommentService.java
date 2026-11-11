package com.tripdiary.comment;

import com.tripdiary.comment.dto.CommentPageResponse;
import com.tripdiary.comment.dto.CommentRequest;
import com.tripdiary.comment.dto.CommentResponse;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.spot.Spot;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotService;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** コメント（docs/features/05_comment.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    static final String COMMENT_NOT_FOUND = "COMMENT_NOT_FOUND";

    private final CommentRepository commentRepository;
    private final SpotService spotService;
    private final UserRepository userRepository;
    private final PhotoStorage photoStorage;
    private final Clock clock;

    /**
     * コメント一覧（古い順）。他人の非公開スポットは SPOT_NOT_FOUND。
     *
     * @param viewerId 閲覧者（未ログインなら null）
     * @param cursor 前のページの nextCursor（最後に返したコメントの投稿日時と ID）
     */
    @Transactional(readOnly = true)
    public CommentPageResponse list(long spotId, Long viewerId, String cursor, int limit) {
        spotService.getVisible(spotId, viewerId);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<Comment> comments;
        if (cursor == null) {
            comments = commentRepository.findFirstPage(spotId, page);
        } else {
            SpotCursor after = SpotCursor.decode(cursor);
            comments = commentRepository.findAfter(spotId, after.createdAt(), after.id(), page);
        }
        boolean hasNext = comments.size() > limit;
        List<Comment> items = hasNext ? comments.subList(0, limit) : comments;
        String nextCursor = null;
        if (hasNext) {
            Comment last = items.get(items.size() - 1);
            nextCursor = new SpotCursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new CommentPageResponse(items.stream().map(this::toResponse).toList(), nextCursor);
    }

    @Transactional
    public CommentResponse create(long spotId, long userId, CommentRequest request) {
        Spot spot = spotService.getVisible(spotId, userId);
        User author = userRepository.getReferenceById(userId);
        Comment comment =
                commentRepository.save(
                        new Comment(spot, author, request.body().strip(), clock.instant()));
        log.info("Comment created commentId={} spotId={}", comment.getId(), spotId);
        return toResponse(comment);
    }

    /** 編集はコメントの投稿者本人のみ（他人は 403）。 */
    @Transactional
    public CommentResponse update(long commentId, long userId, CommentRequest request) {
        Comment comment = findAccessible(commentId, userId);
        if (!comment.isWrittenBy(userId)) {
            throw new ForbiddenException("他のユーザーのコメントは編集できません");
        }
        comment.edit(request.body().strip(), clock.instant());
        log.info("Comment updated commentId={}", commentId);
        return toResponse(comment);
    }

    /** 削除はコメントの投稿者本人、またはコメントが付いたスポットの投稿者（それ以外は 403）。 */
    @Transactional
    public void delete(long commentId, long userId) {
        Comment comment = findAccessible(commentId, userId);
        if (!comment.isWrittenBy(userId) && !comment.getSpot().isOwnedBy(userId)) {
            throw new ForbiddenException("このコメントは削除できません");
        }
        commentRepository.delete(comment);
        log.info("Comment deleted commentId={}", commentId);
    }

    /** 操作できるコメント。存在しない・見えないスポット（他人の非公開）のコメントは、存在を知られないよう 404。 */
    private Comment findAccessible(long commentId, long userId) {
        return commentRepository
                .findWithSpotById(commentId)
                .filter(c -> c.getSpot().isVisibleTo(userId))
                .orElseThrow(() -> new NotFoundException(COMMENT_NOT_FOUND, "指定されたコメントは存在しません"));
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.from(comment, photoStorage::publicUrl);
    }
}
