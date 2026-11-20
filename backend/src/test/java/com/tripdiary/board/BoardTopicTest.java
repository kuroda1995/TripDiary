package com.tripdiary.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripdiary.board.dto.BoardTopicSummaryResponse;
import com.tripdiary.support.TestUsers;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 掲示板のトピックの単体テスト。 */
class BoardTopicTest {

    private static final Instant T0 = Instant.parse("2026-11-20T00:00:00.123456789Z");

    @Test
    @DisplayName("作成時は最終書き込み日時を作成日時（マイクロ秒に切り捨て）にそろえる")
    void createdAtIsLastActivity() {
        BoardTopic topic = new BoardTopic(TestUsers.user(1L, "alice"), "t", "b", T0);

        assertThat(topic.getCreatedAt()).isEqualTo(Instant.parse("2026-11-20T00:00:00.123456Z"));
        assertThat(topic.getLastActivityAt()).isEqualTo(topic.getCreatedAt());
    }

    @Test
    @DisplayName("返信で最終書き込み日時を進めるが、過去の日時では戻さない")
    void touchOnlyMovesForward() {
        BoardTopic topic = new BoardTopic(TestUsers.user(1L, "alice"), "t", "b", T0);

        topic.touch(T0.plusSeconds(60));
        assertThat(topic.getLastActivityAt())
                .isEqualTo(Instant.parse("2026-11-20T00:01:00.123456Z"));

        topic.touch(T0);
        assertThat(topic.getLastActivityAt())
                .isEqualTo(Instant.parse("2026-11-20T00:01:00.123456Z"));
    }

    @Test
    @DisplayName("一覧の本文は120文字を超えると切り詰めて…を付ける")
    void excerpt() {
        BoardTopic shortTopic = new BoardTopic(TestUsers.user(1L, "alice"), "t", "短い本文", T0);
        BoardTopic longTopic =
                new BoardTopic(TestUsers.user(1L, "alice"), "t", "あ".repeat(121), T0);
        ReflectionTestUtils.setField(shortTopic, "id", 1L);
        ReflectionTestUtils.setField(longTopic, "id", 2L);

        assertThat(BoardTopicSummaryResponse.from(shortTopic, 0, key -> key).bodyExcerpt())
                .isEqualTo("短い本文");
        assertThat(BoardTopicSummaryResponse.from(longTopic, 0, key -> key).bodyExcerpt())
                .isEqualTo("あ".repeat(120) + "…");
    }

    @Test
    @DisplayName("投稿者の判定")
    void isWrittenBy() {
        BoardTopic topic = new BoardTopic(TestUsers.user(1L, "alice"), "t", "b", T0);

        assertThat(topic.isWrittenBy(1L)).isTrue();
        assertThat(topic.isWrittenBy(2L)).isFalse();
    }
}
