package com.tripdiary.follow;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * フォロー関係（{@code follows}）の読み書き。
 *
 * <p>主キー {@code (follower_id, followee_id)} だけのテーブルのため、JPA のエンティティにせず SQL で扱う。 プロフィールに出す件数は {@link
 * com.tripdiary.user.JdbcUserStatsReader} がまとめて読む。
 */
@Repository
@RequiredArgsConstructor
public class FollowRepository {

    /** 閲覧者がいない場合に、どのユーザーにも一致しない値として使う。 */
    private static final long NO_VIEWER = -1;

    private final NamedParameterJdbcTemplate jdbc;

    /** フォローする。すでにフォローしていれば何もしない（冪等。docs/features/06_follow.md 4.2）。 */
    public void insertIfAbsent(long followerId, long followeeId) {
        jdbc.update(
                "INSERT INTO follows (follower_id, followee_id) VALUES (:followerId, :followeeId)"
                        + " ON CONFLICT DO NOTHING",
                Map.of("followerId", followerId, "followeeId", followeeId));
    }

    /** フォロー解除。フォローしていなければ何もしない（冪等）。 */
    public void delete(long followerId, long followeeId) {
        jdbc.update(
                "DELETE FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId",
                Map.of("followerId", followerId, "followeeId", followeeId));
    }

    public long countFollowers(long userId) {
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM follows WHERE followee_id = :userId",
                        Map.of("userId", userId),
                        Long.class);
        return count == null ? 0 : count;
    }

    /** 一覧の1行（相手のユーザーと、フォローした日時・閲覧者がその人をフォローしているか）。 */
    public record FollowRow(
            long userId,
            String username,
            String displayName,
            String avatarS3Key,
            Instant followedAt,
            boolean followedByViewer) {}

    /** 位置（フォローした日時と相手のユーザー ID）。カーソルに使う。 */
    public record Position(Instant followedAt, long userId) {}

    /** {@code userId} がフォローしている人（フォローした日時の新しい順）。 */
    public List<FollowRow> findFollowing(long userId, Long viewerId, Position after, int limit) {
        return findList("follower_id", "followee_id", userId, viewerId, after, limit);
    }

    /** {@code userId} をフォローしている人（フォローされた日時の新しい順）。 */
    public List<FollowRow> findFollowers(long userId, Long viewerId, Position after, int limit) {
        return findList("followee_id", "follower_id", userId, viewerId, after, limit);
    }

    /**
     * @param ownerColumn 一覧の持ち主の列（固定の列名のみ。利用者の入力は渡さない）
     * @param otherColumn 一覧に並べる相手の列
     */
    private List<FollowRow> findList(
            String ownerColumn,
            String otherColumn,
            long userId,
            Long viewerId,
            Position after,
            int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("viewerId", viewerId == null ? NO_VIEWER : viewerId)
                        .addValue("limit", limit);
        String afterCondition = "";
        if (after != null) {
            afterCondition =
                    " AND (f.created_at < :followedAt"
                            + " OR (f.created_at = :followedAt AND f.%s < :afterUserId))"
                                    .formatted(otherColumn);
            params.addValue("followedAt", Timestamp.from(after.followedAt()))
                    .addValue("afterUserId", after.userId());
        }
        String sql =
                """
                SELECT u.id, u.username, u.display_name, u.avatar_s3_key, f.created_at,
                       EXISTS (SELECT 1 FROM follows v
                               WHERE v.follower_id = :viewerId AND v.followee_id = u.id)
                           AS followed_by_viewer
                FROM follows f JOIN users u ON u.id = f.%2$s
                WHERE f.%1$s = :userId%3$s
                ORDER BY f.created_at DESC, f.%2$s DESC
                LIMIT :limit
                """
                        .formatted(ownerColumn, otherColumn, afterCondition);
        return jdbc.query(
                sql,
                params,
                (rs, row) ->
                        new FollowRow(
                                rs.getLong("id"),
                                rs.getString("username"),
                                rs.getString("display_name"),
                                rs.getString("avatar_s3_key"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getBoolean("followed_by_viewer")));
    }

    /** タイムラインの1件（スポット ID と投稿日時）。 */
    public record TimelineEntry(long spotId, Instant createdAt) {}

    /**
     * フォロー中ユーザーの公開スポットを新着順に返す（docs/features/06_follow.md 5章）。
     *
     * <p>自分自身はフォローできないため、自分の投稿は含まれない。
     *
     * @param after この位置より後（カーソル。最初のページは null）
     */
    public List<TimelineEntry> findTimeline(long userId, TimelineEntry after, int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit);
        String afterCondition = "";
        if (after != null) {
            afterCondition =
                    " AND (s.created_at < :createdAt"
                            + " OR (s.created_at = :createdAt AND s.id < :spotId))";
            params.addValue("createdAt", Timestamp.from(after.createdAt()))
                    .addValue("spotId", after.spotId());
        }
        return jdbc.query(
                "SELECT s.id, s.created_at FROM spots s"
                        + " JOIN follows f ON f.followee_id = s.user_id"
                        + " WHERE f.follower_id = :userId AND s.visibility = 'PUBLIC'"
                        + afterCondition
                        + " ORDER BY s.created_at DESC, s.id DESC LIMIT :limit",
                params,
                (rs, row) ->
                        new TimelineEntry(
                                rs.getLong("id"), rs.getTimestamp("created_at").toInstant()));
    }
}
