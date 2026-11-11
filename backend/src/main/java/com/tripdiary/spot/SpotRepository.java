package com.tripdiary.spot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * スポットの取得。
 *
 * <p>一覧は「投稿日時の降順、同時刻は ID の降順」のカーソルページング。 投稿者は fetch join、エリアタグは hibernate.default_batch_fetch_size
 * によりまとめて取得し、N+1 を避ける。
 */
public interface SpotRepository extends JpaRepository<Spot, Long> {

    @EntityGraph(attributePaths = {"user", "areaTags"})
    Optional<Spot> findWithDetailsById(Long id);

    /** 指定した ID のスポット（順不同。投稿者は fetch join、エリアタグ・写真はまとめて取得）。 */
    @Query("select s from Spot s join fetch s.user where s.id in :ids")
    List<Spot> findAllWithUserByIdIn(@Param("ids") java.util.Collection<Long> ids);

    @Query(
            "select s from Spot s join fetch s.user"
                    + " where s.visibility = com.tripdiary.spot.Visibility.PUBLIC"
                    + " order by s.createdAt desc, s.id desc")
    List<Spot> findPublicFirstPage(Pageable pageable);

    @Query(
            "select s from Spot s join fetch s.user"
                    + " where s.visibility = com.tripdiary.spot.Visibility.PUBLIC"
                    + " and (s.createdAt < :createdAt or (s.createdAt = :createdAt and s.id < :id))"
                    + " order by s.createdAt desc, s.id desc")
    List<Spot> findPublicAfter(
            @Param("createdAt") Instant createdAt, @Param("id") long id, Pageable pageable);

    /** ユーザー別一覧。{@code includePrivate} が true（本人が見る場合）なら非公開も含める。 */
    @Query(
            "select s from Spot s join fetch s.user u"
                    + " where u.id = :userId"
                    + " and (:includePrivate = true or s.visibility = com.tripdiary.spot.Visibility.PUBLIC)"
                    + " order by s.createdAt desc, s.id desc")
    List<Spot> findByUserFirstPage(
            @Param("userId") long userId,
            @Param("includePrivate") boolean includePrivate,
            Pageable pageable);

    @Query(
            "select s from Spot s join fetch s.user u"
                    + " where u.id = :userId"
                    + " and (:includePrivate = true or s.visibility = com.tripdiary.spot.Visibility.PUBLIC)"
                    + " and (s.createdAt < :createdAt or (s.createdAt = :createdAt and s.id < :id))"
                    + " order by s.createdAt desc, s.id desc")
    List<Spot> findByUserAfter(
            @Param("userId") long userId,
            @Param("includePrivate") boolean includePrivate,
            @Param("createdAt") Instant createdAt,
            @Param("id") long id,
            Pageable pageable);
}
