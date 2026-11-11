package com.tripdiary.status;

import com.tripdiary.spot.SpotStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ステータス（{@code user_spot_statuses}）の読み書き。
 *
 * <p>主キー {@code (user_id, spot_id)} の小さなテーブルのため、JPA のエンティティにせず SQL で扱う。 一覧・詳細に出す閲覧者のステータスは {@link
 * com.tripdiary.spot.JdbcSpotReactionReader} がまとめて読む。
 */
@Repository
@RequiredArgsConstructor
public class SpotStatusRepository {

    /** マイリスト・件数に出すスポットの条件（公開、または自分の投稿。後から非公開になった他人の投稿は出さない）。 */
    private static final String VISIBLE = "(s.visibility = 'PUBLIC' OR s.user_id = st.user_id)";

    private final NamedParameterJdbcTemplate jdbc;

    /** 1件のステータス。 */
    public record StatusRow(long spotId, SpotStatus status, Instant visitedAt, Instant updatedAt) {}

    private static final RowMapper<StatusRow> ROW_MAPPER =
            (rs, row) -> {
                Timestamp visitedAt = rs.getTimestamp("visited_at");
                return new StatusRow(
                        rs.getLong("spot_id"),
                        SpotStatus.valueOf(rs.getString("status")),
                        visitedAt == null ? null : visitedAt.toInstant(),
                        rs.getTimestamp("updated_at").toInstant());
            };

    /**
     * 設定・変更する（UPSERT。docs/features/08_spot_status.md 5章）。
     *
     * <p>同じ値で設定し直した場合は何も変えない（訪問日時・リストの並び順を保つ）。 「訪問済み」にしたときだけ訪問日時を記録し、「行きたい」に戻すと消す。
     *
     * @param now 記録する日時（DB の精度に合わせて切り捨て済みのもの）
     */
    public void upsert(long userId, long spotId, SpotStatus status, Instant now) {
        Timestamp at = Timestamp.from(now);
        jdbc.update(
                """
                INSERT INTO user_spot_statuses
                    (user_id, spot_id, status, visited_at, created_at, updated_at)
                VALUES (:userId, :spotId, :status, :visitedAt, :now, :now)
                ON CONFLICT (user_id, spot_id) DO UPDATE
                    SET status = EXCLUDED.status,
                        visited_at = EXCLUDED.visited_at,
                        updated_at = EXCLUDED.updated_at
                    WHERE user_spot_statuses.status <> EXCLUDED.status
                """,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("spotId", spotId)
                        .addValue("status", status.name())
                        .addValue("visitedAt", status == SpotStatus.VISITED ? at : null)
                        .addValue("now", at));
    }

    /** 解除する。付いていなければ何もしない（冪等）。 */
    public void delete(long userId, long spotId) {
        jdbc.update(
                "DELETE FROM user_spot_statuses WHERE user_id = :userId AND spot_id = :spotId",
                Map.of("userId", userId, "spotId", spotId));
    }

    public Optional<StatusRow> find(long userId, long spotId) {
        return jdbc
                .query(
                        "SELECT spot_id, status, visited_at, updated_at FROM user_spot_statuses"
                                + " WHERE user_id = :userId AND spot_id = :spotId",
                        Map.of("userId", userId, "spotId", spotId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    /** 位置（更新日時とスポット ID）。カーソルに使う。 */
    public record Position(Instant updatedAt, long spotId) {}

    /**
     * マイリスト（更新日時の新しい順）。
     *
     * @param after この位置より後（カーソル。最初のページは null）
     */
    public List<StatusRow> findByStatus(long userId, SpotStatus status, Position after, int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("status", status.name())
                        .addValue("limit", limit);
        String afterCondition = "";
        if (after != null) {
            afterCondition = " AND (st.updated_at, st.spot_id) < (:afterUpdatedAt, :afterSpotId)";
            params.addValue("afterUpdatedAt", Timestamp.from(after.updatedAt()))
                    .addValue("afterSpotId", after.spotId());
        }
        return jdbc.query(
                "SELECT st.spot_id, st.status, st.visited_at, st.updated_at"
                        + " FROM user_spot_statuses st JOIN spots s ON s.id = st.spot_id"
                        + " WHERE st.user_id = :userId AND st.status = :status AND "
                        + VISIBLE
                        + afterCondition
                        + " ORDER BY st.updated_at DESC, st.spot_id DESC LIMIT :limit",
                params,
                ROW_MAPPER);
    }

    /** ステータスごとの件数（マイリストと同じく、見えなくなった他人の投稿は数えない）。 */
    public Map<SpotStatus, Long> countByStatus(long userId) {
        Map<SpotStatus, Long> counts = new java.util.EnumMap<>(SpotStatus.class);
        for (SpotStatus status : SpotStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query(
                "SELECT st.status, count(*) AS cnt"
                        + " FROM user_spot_statuses st JOIN spots s ON s.id = st.spot_id"
                        + " WHERE st.user_id = :userId AND "
                        + VISIBLE
                        + " GROUP BY st.status",
                Map.of("userId", userId),
                rs -> {
                    counts.put(SpotStatus.valueOf(rs.getString("status")), rs.getLong("cnt"));
                });
        return counts;
    }
}
