package com.tripdiary.comment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** コメントの取得（古い順のカーソルページング。投稿者は fetch join で N+1 を避ける）。 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query(
            "select c from Comment c join fetch c.user"
                    + " where c.spot.id = :spotId order by c.createdAt asc, c.id asc")
    List<Comment> findFirstPage(@Param("spotId") long spotId, Pageable pageable);

    @Query(
            "select c from Comment c join fetch c.user"
                    + " where c.spot.id = :spotId"
                    + " and (c.createdAt > :createdAt or (c.createdAt = :createdAt and c.id > :id))"
                    + " order by c.createdAt asc, c.id asc")
    List<Comment> findAfter(
            @Param("spotId") long spotId,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            Pageable pageable);

    /** 権限の確認に使うため、スポットとその投稿者・コメントの投稿者もまとめて取得する。 */
    @Query(
            "select c from Comment c join fetch c.user join fetch c.spot s join fetch s.user"
                    + " where c.id = :id")
    Optional<Comment> findWithSpotById(@Param("id") long id);
}
