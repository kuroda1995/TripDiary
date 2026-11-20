package com.tripdiary.community;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * コミュニティへの参加関係（{@code community_members}）の読み書き。
 *
 * <p>主キー {@code (community_id, user_id)} の中間テーブルのため、行ってみたい！（LikeRepository）と同じく SQL で扱う。 メンバー数は
 * {@code communities.member_count}（このテーブルのトリガーで保たれる。V9）を読む。
 */
@Repository
@RequiredArgsConstructor
public class MembershipRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** メンバー一覧の1件。 */
    public record MemberRow(long userId, Instant joinedAt) {}

    /** 参加する。すでに参加していれば何もしない（冪等）。 */
    public void insertIfAbsent(long communityId, long userId, Instant joinedAt) {
        jdbc.update(
                "INSERT INTO community_members (community_id, user_id, joined_at)"
                        + " VALUES (:communityId, :userId, :joinedAt) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource("communityId", communityId)
                        .addValue("userId", userId)
                        .addValue("joinedAt", Timestamp.from(joinedAt)));
    }

    /** 退出する。参加していなければ何もしない（冪等）。 */
    public void delete(long communityId, long userId) {
        jdbc.update(
                "DELETE FROM community_members WHERE community_id = :communityId AND user_id = :userId",
                Map.of("communityId", communityId, "userId", userId));
    }

    public boolean isMember(long communityId, long userId) {
        Boolean exists =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM community_members"
                                + " WHERE community_id = :communityId AND user_id = :userId)",
                        Map.of("communityId", communityId, "userId", userId),
                        Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** 指定したコミュニティのうち、ユーザーが参加しているものの ID（一覧の「参加中」表示用。1回の SQL で読む）。 */
    public Set<Long> joinedCommunityIds(long userId, Collection<Long> communityIds) {
        if (communityIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.queryForList(
                        "SELECT community_id FROM community_members"
                                + " WHERE user_id = :userId AND community_id IN (:communityIds)",
                        Map.of("userId", userId, "communityIds", communityIds),
                        Long.class));
    }

    /** メンバー数（{@code communities.member_count}。参加・退出と同じトランザクション内でトリガーが更新済み）。 */
    public long memberCount(long communityId) {
        Long count =
                jdbc.queryForObject(
                        "SELECT member_count FROM communities WHERE id = :communityId",
                        Map.of("communityId", communityId),
                        Long.class);
        return count == null ? 0 : count;
    }

    /** メンバー一覧（参加した順。idx_community_members_community_joined を使う）。 */
    public List<MemberRow> findMembers(long communityId, MemberRow after, int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("communityId", communityId).addValue("limit", limit);
        String afterCondition = "";
        if (after != null) {
            afterCondition = " AND (joined_at, user_id) > (:afterJoinedAt, :afterUserId)";
            params.addValue("afterJoinedAt", Timestamp.from(after.joinedAt()))
                    .addValue("afterUserId", after.userId());
        }
        return jdbc.query(
                "SELECT user_id, joined_at FROM community_members"
                        + " WHERE community_id = :communityId"
                        + afterCondition
                        + " ORDER BY joined_at, user_id LIMIT :limit",
                params,
                (rs, row) ->
                        new MemberRow(
                                rs.getLong("user_id"), rs.getTimestamp("joined_at").toInstant()));
    }

    /** コミュニティ内のトピック数（詳細画面に出す）。 */
    public long topicCount(long communityId) {
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM board_topics WHERE community_id = :communityId",
                        Map.of("communityId", communityId),
                        Long.class);
        return count == null ? 0 : count;
    }
}
