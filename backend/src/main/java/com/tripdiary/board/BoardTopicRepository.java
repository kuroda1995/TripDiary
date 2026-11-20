package com.tripdiary.board;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** トピックの取得（最後の書き込みの新しい順のカーソルページング。投稿者は fetch join で N+1 を避ける）。 */
public interface BoardTopicRepository extends JpaRepository<BoardTopic, Long> {

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findFirstPage(Pageable pageable);

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " where t.lastActivityAt < :lastActivityAt"
                    + " or (t.lastActivityAt = :lastActivityAt and t.id < :id)"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findAfter(
            @Param("lastActivityAt") Instant lastActivityAt,
            @Param("id") long id,
            Pageable pageable);

    @Query("select t from BoardTopic t join fetch t.user where t.id = :id")
    Optional<BoardTopic> findWithUserById(@Param("id") long id);
}
