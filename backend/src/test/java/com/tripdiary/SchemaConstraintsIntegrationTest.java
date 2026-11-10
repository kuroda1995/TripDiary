package com.tripdiary;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V1 マイグレーションの主な制約が設計書（docs/database_schema.md）どおりに効いていることを確認する。 */
@IntegrationTest
class SchemaConstraintsIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        // area_tags は初期データ（V2）を他のテストでも使うため、このテストで追加した行だけを消す
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        jdbc.execute("DELETE FROM area_tags WHERE slug LIKE 'test-%'");
    }

    private long insertUser(String email, String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, username, password_hash, display_name)"
                        + " VALUES (?, ?, 'hash', 'name') RETURNING id",
                Long.class,
                email,
                username);
    }

    private long insertSpot(long userId) {
        return jdbc.queryForObject(
                "INSERT INTO spots (user_id, title, location_name) VALUES (?, '清水寺', '京都') RETURNING id",
                Long.class,
                userId);
    }

    @Test
    @DisplayName("メールアドレスは大文字小文字を区別せず一意")
    void emailUniqueIgnoringCase() {
        insertUser("taro@example.com", "taro");

        assertThatThrownBy(() -> insertUser("TARO@example.com", "taro2"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ユーザー名は大文字小文字を区別せず一意")
    void usernameUniqueIgnoringCase() {
        insertUser("a@example.com", "taro_trip");

        assertThatThrownBy(() -> insertUser("b@example.com", "TARO_TRIP"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("ユーザー名の形式（半角英数字と_、3〜30文字）以外は登録できない")
    void usernameFormat() {
        assertThatThrownBy(() -> insertUser("a@example.com", "ab"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("b@example.com", "たろう"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("自分自身はフォローできない")
    void cannotFollowSelf() {
        long userId = insertUser("a@example.com", "alice");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)",
                                        userId,
                                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("同じスポットへの行ってみたい！は1ユーザー1回まで")
    void likeOncePerUser() {
        long userId = insertUser("a@example.com", "alice");
        long spotId = insertSpot(userId);
        jdbc.update("INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)", userId, spotId);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)",
                                        userId,
                                        spotId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("visited_at は VISITED のときだけ設定できる")
    void visitedAtOnlyForVisited() {
        long userId = insertUser("a@example.com", "alice");
        long spotId = insertSpot(userId);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO user_spot_statuses (user_id, spot_id, status, visited_at)"
                                                + " VALUES (?, ?, 'WANT_TO_GO', now())",
                                        userId,
                                        spotId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(
                        () ->
                                jdbc.update(
                                        "INSERT INTO user_spot_statuses (user_id, spot_id, status, visited_at)"
                                                + " VALUES (?, ?, 'VISITED', now())",
                                        userId,
                                        spotId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ユーザーを削除すると投稿・いいね・フォローも連鎖削除される")
    void deleteUserCascades() {
        long alice = insertUser("a@example.com", "alice");
        long bob = insertUser("b@example.com", "bob");
        long spotId = insertSpot(alice);
        jdbc.update("INSERT INTO spot_likes (user_id, spot_id) VALUES (?, ?)", bob, spotId);
        jdbc.update("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)", bob, alice);

        jdbc.update("DELETE FROM users WHERE id = ?", alice);

        Integer remaining =
                jdbc.queryForObject(
                        "SELECT (SELECT count(*) FROM spots) + (SELECT count(*) FROM spot_likes)"
                                + " + (SELECT count(*) FROM follows)",
                        Integer.class);
        org.assertj.core.api.Assertions.assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("最上位のエリアタグ同士でも同じ名前は登録できない（NULLS NOT DISTINCT）")
    void areaTagNameUniqueAtTopLevel() {
        jdbc.update("INSERT INTO area_tags (name, slug, level) VALUES ('テスト地方', 'test-region', 1)");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO area_tags (name, slug, level)"
                                                + " VALUES ('テスト地方', 'test-region-2', 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
