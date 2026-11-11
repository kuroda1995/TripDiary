package com.tripdiary.spot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 公開スポットの検索（docs/features/07_area_search.md 4.2・5章）。
 *
 * <p>条件が可変で、人気順は行ってみたい！数で並べるため SQL で組み立てる。ここでは ID と並び替えの値だけを返し、 一覧の形への変換は {@link
 * SpotService#summariesInOrder} が行う。
 */
@Repository
@RequiredArgsConstructor
public class SpotSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 検索条件。
     *
     * @param areaTagIds 対象のエリアタグ ID（子孫タグ展開済み。空なら絞り込まない）
     * @param keyword タイトル・場所名の部分一致（null なら絞り込まない）
     */
    public record Criteria(Collection<Long> areaTagIds, String keyword, SpotSort sort) {}

    /** 検索結果の1件（カーソルを作るための並び替えの値を含む）。 */
    public record Hit(long id, Instant createdAt, long likeCount) {}

    /**
     * @param after この位置より後（カーソル。最初のページは null。{@code sort} と同じ並び順の値）
     */
    public List<Hit> search(Criteria criteria, Hit after, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        List<String> conditions = new ArrayList<>();
        conditions.add("s.visibility = 'PUBLIC'");
        if (!criteria.areaTagIds().isEmpty()) {
            // EXISTS にすることで、複数の指定タグに当てはまる投稿も1件になる
            conditions.add(
                    "EXISTS (SELECT 1 FROM spot_area_tags sat"
                            + " WHERE sat.spot_id = s.id AND sat.area_tag_id IN (:areaTagIds))");
            params.addValue("areaTagIds", criteria.areaTagIds());
        }
        if (criteria.keyword() != null) {
            // ILIKE は idx_spots_title_trgm / idx_spots_location_trgm（pg_trgm）を使える
            conditions.add(
                    "(s.title ILIKE :pattern ESCAPE '\\' OR s.location_name ILIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", "%" + escapeLike(criteria.keyword()) + "%");
        }

        String order;
        if (criteria.sort() == SpotSort.POPULAR) {
            if (after != null) {
                conditions.add(
                        "(lc.like_count, s.created_at, s.id) < (:afterLikeCount, :afterCreatedAt, :afterId)");
                params.addValue("afterLikeCount", after.likeCount());
            }
            order = "lc.like_count DESC, s.created_at DESC, s.id DESC";
        } else {
            if (after != null) {
                conditions.add("(s.created_at, s.id) < (:afterCreatedAt, :afterId)");
            }
            order = "s.created_at DESC, s.id DESC";
        }
        if (after != null) {
            params.addValue("afterCreatedAt", Timestamp.from(after.createdAt()))
                    .addValue("afterId", after.id());
        }

        String sql =
                """
                SELECT s.id, s.created_at, lc.like_count
                FROM spots s
                CROSS JOIN LATERAL (SELECT count(*) AS like_count
                                    FROM spot_likes l WHERE l.spot_id = s.id) lc
                WHERE %s
                ORDER BY %s
                LIMIT :limit
                """
                        .formatted(String.join(" AND ", conditions), order);
        return jdbc.query(
                sql,
                params,
                (rs, row) ->
                        new Hit(
                                rs.getLong("id"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getLong("like_count")));
    }

    /** LIKE のワイルドカード（%・_）とエスケープ文字を、文字そのものとして扱わせる。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
