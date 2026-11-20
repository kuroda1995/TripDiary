package com.tripdiary.board;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 返信の取得（古い順のカーソルページング。投稿者は fetch join で N+1 を避ける）。 */
public interface BoardReplyRepository extends JpaRepository<BoardReply, Long> {

    @Query(
            "select r from BoardReply r join fetch r.user"
                    + " where r.topic.id = :topicId order by r.createdAt asc, r.id asc")
    List<BoardReply> findFirstPage(@Param("topicId") long topicId, Pageable pageable);

    @Query(
            "select r from BoardReply r join fetch r.user"
                    + " where r.topic.id = :topicId"
                    + " and (r.createdAt > :createdAt or (r.createdAt = :createdAt and r.id > :id))"
                    + " order by r.createdAt asc, r.id asc")
    List<BoardReply> findAfter(
            @Param("topicId") long topicId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            Pageable pageable);

    /** 権限の確認に使うため、トピックとその投稿者・返信の投稿者・コミュニティのオーナーもまとめて取得する。 */
    @Query(
            "select r from BoardReply r join fetch r.user join fetch r.topic t join fetch t.user"
                    + " left join fetch t.community c left join fetch c.owner"
                    + " where r.id = :id")
    Optional<BoardReply> findWithTopicById(@Param("id") long id);

    long countByTopicId(long topicId);

    /** トピックごとの返信数（idx_board_replies_topic_id_created_at で数える）。 */
    @Query(
            "select r.topic.id as topicId, count(r) as replyCount from BoardReply r"
                    + " where r.topic.id in :topicIds group by r.topic.id")
    List<ReplyCount> countByTopicIds(@Param("topicIds") Collection<Long> topicIds);

    interface ReplyCount {
        Long getTopicId();

        Long getReplyCount();
    }
}
