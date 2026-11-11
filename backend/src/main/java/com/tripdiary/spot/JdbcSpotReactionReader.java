package com.tripdiary.spot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 行ってみたい！の件数・閲覧者が付けたか・コメント件数・閲覧者のステータスを、スポットの数に関係なく1回の SQL で取得する。
 *
 * <p>テーブル（{@code spot_likes} / {@code comments} / {@code
 * user_spot_statuses}）を直接読むため、like・comment・status パッケージには依存しない。 行ってみたい！の件数は {@code
 * spots.like_count}（{@code spot_likes} のトリガーで保たれる。V5）を読み、コメント件数はインデックス（{@code
 * idx_comments_spot_id_created_at}）で数える。
 */
@Component
@RequiredArgsConstructor
public class JdbcSpotReactionReader implements SpotReactionReader {

    /** 閲覧者がいない場合に、どのユーザーにも一致しない値として使う。 */
    private static final long NO_VIEWER = -1;

    private static final String SQL =
            """
            SELECT s.id,
                   s.like_count,
                   EXISTS (SELECT 1 FROM spot_likes l
                           WHERE l.spot_id = s.id AND l.user_id = :viewerId) AS liked,
                   (SELECT count(*) FROM comments c WHERE c.spot_id = s.id) AS comment_count,
                   (SELECT st.status FROM user_spot_statuses st
                    WHERE st.spot_id = s.id AND st.user_id = :viewerId) AS my_status
            FROM spots s
            WHERE s.id IN (:spotIds)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<Long, SpotReactions> read(Collection<Long> spotIds, Long viewerId) {
        Map<Long, SpotReactions> result = new HashMap<>();
        if (spotIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                SQL,
                new MapSqlParameterSource()
                        .addValue("spotIds", spotIds)
                        .addValue("viewerId", viewerId == null ? NO_VIEWER : viewerId),
                rs -> {
                    String myStatus = rs.getString("my_status");
                    result.put(
                            rs.getLong("id"),
                            new SpotReactions(
                                    rs.getLong("like_count"),
                                    rs.getBoolean("liked"),
                                    rs.getLong("comment_count"),
                                    myStatus == null ? null : SpotStatus.valueOf(myStatus)));
                });
        return result;
    }
}
