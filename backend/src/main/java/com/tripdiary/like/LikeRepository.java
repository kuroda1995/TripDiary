package com.tripdiary.like;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 行ってみたい！（{@code spot_likes}）の読み書き。
 *
 * <p>主キー {@code (user_id, spot_id)} だけのテーブルのため、JPA のエンティティにせず SQL で扱う。 一覧・詳細に出す件数は {@link
 * com.tripdiary.spot.JdbcSpotReactionReader} がまとめて読む。
 */
@Repository
@RequiredArgsConstructor
public class LikeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 付ける。すでに付いていれば何もしない（冪等。docs/features/04_like.md 5章）。 */
    public void insertIfAbsent(long userId, long spotId) {
        jdbc.update(
                "INSERT INTO spot_likes (user_id, spot_id) VALUES (:userId, :spotId)"
                        + " ON CONFLICT DO NOTHING",
                Map.of("userId", userId, "spotId", spotId));
    }

    /** 外す。付いていなければ何もしない（冪等）。 */
    public void delete(long userId, long spotId) {
        jdbc.update(
                "DELETE FROM spot_likes WHERE user_id = :userId AND spot_id = :spotId",
                Map.of("userId", userId, "spotId", spotId));
    }

    /** 件数（{@code spots.like_count}。付け外しと同じトランザクション内でトリガーが更新済み。V5）。 */
    public long count(long spotId) {
        return jdbc
                .query(
                        "SELECT like_count FROM spots WHERE id = :spotId",
                        Map.of("spotId", spotId),
                        (rs, row) -> rs.getLong("like_count"))
                .stream()
                .findFirst()
                .orElse(0L);
    }

    /** 自分が付けたスポット（付けた日時）。 */
    public record LikedSpot(long spotId, Instant likedAt) {}

    /**
     * 自分が付けたスポットを、付けた日時の新しい順に返す。
     *
     * <p>付けた後に非公開になった他人のスポットは含めない。
     *
     * @param after この位置より後（カーソル。最初のページは null）
     */
    public List<LikedSpot> findLiked(long userId, LikedSpot after, int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit);
        String afterCondition = "";
        if (after != null) {
            afterCondition =
                    " AND (l.created_at < :likedAt"
                            + " OR (l.created_at = :likedAt AND l.spot_id < :spotId))";
            params.addValue("likedAt", Timestamp.from(after.likedAt()))
                    .addValue("spotId", after.spotId());
        }
        return jdbc.query(
                "SELECT l.spot_id, l.created_at FROM spot_likes l JOIN spots s ON s.id = l.spot_id"
                        + " WHERE l.user_id = :userId"
                        + " AND (s.visibility = 'PUBLIC' OR s.user_id = :userId)"
                        + afterCondition
                        + " ORDER BY l.created_at DESC, l.spot_id DESC LIMIT :limit",
                params,
                (rs, row) ->
                        new LikedSpot(
                                rs.getLong("spot_id"), rs.getTimestamp("created_at").toInstant()));
    }
}
