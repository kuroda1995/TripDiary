package com.tripdiary.community;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * コミュニティの検索（docs/features/12_communities.md 4.1）。
 *
 * <p>条件と並び順が可変のため SQL で組み立てる（スポット検索の SpotSearchRepository と同じ方式）。ID と並び替えの値だけを返し、 一覧の形への変換は {@link
 * CommunityService} が行う。
 */
@Repository
@RequiredArgsConstructor
public class CommunitySearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param keyword 名前・説明文の部分一致（null なら絞り込まない）
     * @param category カテゴリ（null なら絞り込まない）
     */
    public record Criteria(String keyword, CommunityCategory category, CommunitySort sort) {}

    /**
     * @param after この位置より後（最初のページは null）
     */
    public List<CommunityCursor> search(Criteria criteria, CommunityCursor after, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        List<String> conditions = new ArrayList<>();
        if (criteria.keyword() != null) {
            // ILIKE は idx_communities_name_trgm / idx_communities_description_trgm（pg_trgm）を使える
            conditions.add(
                    "(c.name ILIKE :pattern ESCAPE '\\' OR c.description ILIKE :pattern ESCAPE '\\')");
            params.addValue("pattern", "%" + escapeLike(criteria.keyword()) + "%");
        }
        if (criteria.category() != null) {
            // @> は idx_communities_categories（GIN）を使える
            conditions.add("c.categories @> CAST(ARRAY[:category] AS VARCHAR(30)[])");
            params.addValue("category", criteria.category().name());
        }

        String order;
        if (criteria.sort() == CommunitySort.POPULAR) {
            if (after != null) {
                conditions.add(
                        "(c.member_count, c.created_at, c.id) < (:afterMemberCount, :afterCreatedAt, :afterId)");
                params.addValue("afterMemberCount", after.memberCount());
            }
            order = "c.member_count DESC, c.created_at DESC, c.id DESC";
        } else {
            if (after != null) {
                conditions.add("(c.created_at, c.id) < (:afterCreatedAt, :afterId)");
            }
            order = "c.created_at DESC, c.id DESC";
        }
        if (after != null) {
            params.addValue("afterCreatedAt", Timestamp.from(after.createdAt()))
                    .addValue("afterId", after.id());
        }

        String where = conditions.isEmpty() ? "TRUE" : String.join(" AND ", conditions);
        String sql =
                """
                SELECT c.id, c.created_at, c.member_count
                FROM communities c
                WHERE %s
                ORDER BY %s
                LIMIT :limit
                """
                        .formatted(where, order);
        return jdbc.query(
                sql,
                params,
                (rs, row) ->
                        new CommunityCursor(
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getLong("id"),
                                rs.getLong("member_count")));
    }

    /** LIKE のワイルドカード（%・_）とエスケープ文字を、文字そのものとして扱わせる。 */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
