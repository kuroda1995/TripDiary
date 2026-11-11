package com.tripdiary.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * プロフィールに出す件数（投稿数・フォロー数・フォロワー数）と、閲覧者がフォローしているかを1回の SQL で取得する。
 *
 * <p>テーブル（{@code spots} / {@code follows}）を直接読むため、spot・follow パッケージには依存しない。
 */
@Component
@RequiredArgsConstructor
public class JdbcUserStatsReader {

    /** 閲覧者がいない場合に、どのユーザーにも一致しない値として使う。 */
    private static final long NO_VIEWER = -1;

    private static final String SQL =
            """
            SELECT (SELECT count(*) FROM spots s
                    WHERE s.user_id = :userId
                      AND (s.visibility = 'PUBLIC' OR :userId = :viewerId)) AS spot_count,
                   (SELECT count(*) FROM follows f WHERE f.follower_id = :userId) AS following_count,
                   (SELECT count(*) FROM follows f WHERE f.followee_id = :userId) AS follower_count,
                   EXISTS (SELECT 1 FROM follows f
                           WHERE f.follower_id = :viewerId AND f.followee_id = :userId)
                       AS followed_by_viewer
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** プロフィールの件数。 */
    public record UserStats(
            long spotCount, long followingCount, long followerCount, boolean followedByViewer) {}

    /**
     * 投稿数は公開スポットのみ数える。本人が見る場合は非公開も含める（docs/features/06_follow.md 5章）。
     *
     * @param viewerId 閲覧者（未ログインなら null）
     */
    public UserStats read(long userId, Long viewerId) {
        return jdbc.queryForObject(
                SQL,
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("viewerId", viewerId == null ? NO_VIEWER : viewerId),
                (rs, row) ->
                        new UserStats(
                                rs.getLong("spot_count"),
                                rs.getLong("following_count"),
                                rs.getLong("follower_count"),
                                rs.getBoolean("followed_by_viewer")));
    }
}
