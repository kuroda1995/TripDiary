package com.tripdiary.board;

import com.tripdiary.board.BoardReplyRepository.ReplyCount;
import com.tripdiary.board.dto.BoardReplyPageResponse;
import com.tripdiary.board.dto.BoardReplyRequest;
import com.tripdiary.board.dto.BoardReplyResponse;
import com.tripdiary.board.dto.BoardTopicPageResponse;
import com.tripdiary.board.dto.BoardTopicRequest;
import com.tripdiary.board.dto.BoardTopicResponse;
import com.tripdiary.board.dto.BoardTopicSummaryResponse;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** お寺コミュニティ広場（掲示板。docs/features/10_community_board.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    static final String BOARD_TOPIC_NOT_FOUND = "BOARD_TOPIC_NOT_FOUND";
    static final String BOARD_REPLY_NOT_FOUND = "BOARD_REPLY_NOT_FOUND";

    private final BoardTopicRepository topicRepository;
    private final BoardReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final PhotoStorage photoStorage;
    private final Clock clock;

    /**
     * トピック一覧（最後に書き込みがあった順）。
     *
     * @param cursor 前のページの nextCursor（最後に返したトピックの最終書き込み日時と ID）
     */
    @Transactional(readOnly = true)
    public BoardTopicPageResponse listTopics(String cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit + 1);
        List<BoardTopic> topics;
        if (cursor == null) {
            topics = topicRepository.findFirstPage(page);
        } else {
            SpotCursor after = SpotCursor.decode(cursor);
            topics = topicRepository.findAfter(after.createdAt(), after.id(), page);
        }
        boolean hasNext = topics.size() > limit;
        List<BoardTopic> items = hasNext ? topics.subList(0, limit) : topics;
        String nextCursor = null;
        if (hasNext) {
            BoardTopic last = items.get(items.size() - 1);
            nextCursor = new SpotCursor(last.getLastActivityAt(), last.getId()).encode();
        }
        Map<Long, Long> replyCounts = replyCounts(items);
        return new BoardTopicPageResponse(
                items.stream()
                        .map(
                                topic ->
                                        BoardTopicSummaryResponse.from(
                                                topic,
                                                replyCounts.getOrDefault(topic.getId(), 0L),
                                                photoStorage::publicUrl))
                        .toList(),
                nextCursor);
    }

    @Transactional(readOnly = true)
    public BoardTopicResponse getTopic(long topicId) {
        BoardTopic topic = findTopic(topicId);
        return BoardTopicResponse.from(
                topic, replyRepository.countByTopicId(topicId), photoStorage::publicUrl);
    }

    @Transactional
    public BoardTopicResponse createTopic(long userId, BoardTopicRequest request) {
        User author = userRepository.getReferenceById(userId);
        BoardTopic topic =
                topicRepository.save(
                        new BoardTopic(
                                author,
                                request.title().strip(),
                                request.body().strip(),
                                clock.instant()));
        log.info("Board topic created topicId={}", topic.getId());
        return BoardTopicResponse.from(topic, 0, photoStorage::publicUrl);
    }

    /** 削除はトピックの投稿者本人のみ（他人は 403）。返信もまとめて削除される（ON DELETE CASCADE）。 */
    @Transactional
    public void deleteTopic(long topicId, long userId) {
        BoardTopic topic = findTopic(topicId);
        if (!topic.isWrittenBy(userId)) {
            throw new ForbiddenException("他のユーザーのトピックは削除できません");
        }
        topicRepository.delete(topic);
        log.info("Board topic deleted topicId={}", topicId);
    }

    /** 返信一覧（古い順）。 */
    @Transactional(readOnly = true)
    public BoardReplyPageResponse listReplies(long topicId, String cursor, int limit) {
        findTopic(topicId);
        PageRequest page = PageRequest.of(0, limit + 1);
        List<BoardReply> replies;
        if (cursor == null) {
            replies = replyRepository.findFirstPage(topicId, page);
        } else {
            SpotCursor after = SpotCursor.decode(cursor);
            replies = replyRepository.findAfter(topicId, after.createdAt(), after.id(), page);
        }
        boolean hasNext = replies.size() > limit;
        List<BoardReply> items = hasNext ? replies.subList(0, limit) : replies;
        String nextCursor = null;
        if (hasNext) {
            BoardReply last = items.get(items.size() - 1);
            nextCursor = new SpotCursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new BoardReplyPageResponse(
                items.stream()
                        .map(reply -> BoardReplyResponse.from(reply, photoStorage::publicUrl))
                        .toList(),
                nextCursor);
    }

    /** 返信する。トピックの最終書き込み日時を進め、一覧の上に上げる。 */
    @Transactional
    public BoardReplyResponse createReply(long topicId, long userId, BoardReplyRequest request) {
        BoardTopic topic = findTopic(topicId);
        User author = userRepository.getReferenceById(userId);
        BoardReply reply =
                replyRepository.save(
                        new BoardReply(topic, author, request.body().strip(), clock.instant()));
        topic.touch(reply.getCreatedAt());
        log.info("Board reply created replyId={} topicId={}", reply.getId(), topicId);
        return BoardReplyResponse.from(reply, photoStorage::publicUrl);
    }

    /** 削除は返信の投稿者本人、またはトピックの投稿者（それ以外は 403）。 */
    @Transactional
    public void deleteReply(long replyId, long userId) {
        BoardReply reply =
                replyRepository
                        .findWithTopicById(replyId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                BOARD_REPLY_NOT_FOUND, "指定された返信は存在しません"));
        if (!reply.isWrittenBy(userId) && !reply.getTopic().isWrittenBy(userId)) {
            throw new ForbiddenException("この返信は削除できません");
        }
        replyRepository.delete(reply);
        log.info("Board reply deleted replyId={}", replyId);
    }

    private BoardTopic findTopic(long topicId) {
        return topicRepository
                .findWithUserById(topicId)
                .orElseThrow(
                        () -> new NotFoundException(BOARD_TOPIC_NOT_FOUND, "指定されたトピックは存在しません"));
    }

    /** 件数に比例しない回数（1回）でまとめて数える。 */
    private Map<Long, Long> replyCounts(List<BoardTopic> topics) {
        if (topics.isEmpty()) {
            return Map.of();
        }
        return replyRepository
                .countByTopicIds(topics.stream().map(BoardTopic::getId).toList())
                .stream()
                .collect(Collectors.toMap(ReplyCount::getTopicId, ReplyCount::getReplyCount));
    }
}
