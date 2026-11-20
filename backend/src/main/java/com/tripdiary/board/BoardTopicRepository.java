package com.tripdiary.board;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * トピックの取得（最後の書き込みの新しい順のカーソルページング。投稿者は fetch join で N+1 を避ける）。
 *
 * <p>広場の一覧はコミュニティ外（{@code community_id IS NULL}）のトピックだけ、コミュニティの一覧はそのコミュニティのトピックだけを返す。
 */
public interface BoardTopicRepository extends JpaRepository<BoardTopic, Long> {

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " where t.community is null"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findFirstPage(Pageable pageable);

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " where t.community is null"
                    + " and (t.lastActivityAt < :lastActivityAt"
                    + " or (t.lastActivityAt = :lastActivityAt and t.id < :id))"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findAfter(
            @Param("lastActivityAt") Instant lastActivityAt,
            @Param("id") long id,
            Pageable pageable);

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " where t.community.id = :communityId"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findCommunityFirstPage(
            @Param("communityId") long communityId, Pageable pageable);

    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " where t.community.id = :communityId"
                    + " and (t.lastActivityAt < :lastActivityAt"
                    + " or (t.lastActivityAt = :lastActivityAt and t.id < :id))"
                    + " order by t.lastActivityAt desc, t.id desc")
    List<BoardTopic> findCommunityAfter(
            @Param("communityId") long communityId,
            @Param("lastActivityAt") Instant lastActivityAt,
            @Param("id") long id,
            Pageable pageable);

    /** 権限の確認と詳細の表示に使うため、コミュニティとそのオーナーもまとめて取得する。 */
    @Query(
            "select t from BoardTopic t join fetch t.user"
                    + " left join fetch t.community c left join fetch c.owner"
                    + " where t.id = :id")
    Optional<BoardTopic> findWithUserById(@Param("id") long id);
}
