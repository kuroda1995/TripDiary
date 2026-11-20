package com.tripdiary.helpful;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 参考になった（{@code spot_helpfuls}）の読み書き。
 *
 * <p>主キー {@code (user_id, spot_id)} だけのテーブルのため、行ってみたい！（{@code LikeRepository}）と同じく SQL で扱う。
 * 一覧・詳細に出す件数は {@link com.tripdiary.spot.JdbcSpotReactionReader} がまとめて読む。
 */
@Repository
@RequiredArgsConstructor
public class HelpfulRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 付ける。すでに付いていれば何もしない（冪等）。 */
    public void insertIfAbsent(long userId, long spotId) {
        jdbc.update(
                "INSERT INTO spot_helpfuls (user_id, spot_id) VALUES (:userId, :spotId)"
                        + " ON CONFLICT DO NOTHING",
                Map.of("userId", userId, "spotId", spotId));
    }

    /** 外す。付いていなければ何もしない（冪等）。 */
    public void delete(long userId, long spotId) {
        jdbc.update(
                "DELETE FROM spot_helpfuls WHERE user_id = :userId AND spot_id = :spotId",
                Map.of("userId", userId, "spotId", spotId));
    }

    public long count(long spotId) {
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM spot_helpfuls WHERE spot_id = :spotId",
                        Map.of("spotId", spotId),
                        Long.class);
        return count == null ? 0 : count;
    }
}
