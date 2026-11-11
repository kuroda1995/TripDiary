package com.tripdiary.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripdiary.support.TestUsers;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentTest {

    private static final Instant CREATED = Instant.parse("2026-11-11T03:00:00.123456789Z");

    private static Comment comment() {
        return new Comment(null, TestUsers.user(1L, "alice"), "本文", CREATED);
    }

    @Test
    @DisplayName("作成直後は編集済みではなく、日時は DB と同じマイクロ秒精度に切り捨てる")
    void newCommentIsNotEdited() {
        Comment comment = comment();

        assertThat(comment.isEdited()).isFalse();
        assertThat(comment.getCreatedAt()).isEqualTo("2026-11-11T03:00:00.123456Z");
        assertThat(comment.getUpdatedAt()).isEqualTo(comment.getCreatedAt());
    }

    @Test
    @DisplayName("本文を変えると編集済みになり、同じ本文なら編集済みにしない")
    void edit() {
        Comment unchanged = comment();
        unchanged.edit("本文", CREATED.plusSeconds(60));
        assertThat(unchanged.isEdited()).isFalse();

        Comment changed = comment();
        changed.edit("修正後", CREATED.plusSeconds(60));
        assertThat(changed.isEdited()).isTrue();
        assertThat(changed.getBody()).isEqualTo("修正後");
        assertThat(changed.getUpdatedAt()).isEqualTo("2026-11-11T03:01:00.123456Z");
    }

    @Test
    @DisplayName("作成と同じマイクロ秒（時計の巻き戻りを含む）に編集しても編集済みと分かる")
    void editAtSameMicrosecond() {
        Comment comment = comment();

        comment.edit("修正後", CREATED.plusNanos(100));

        assertThat(comment.isEdited()).isTrue();
        assertThat(comment.getUpdatedAt()).isAfter(comment.getCreatedAt());
    }

    @Test
    @DisplayName("投稿者の判定")
    void writtenBy() {
        assertThat(comment().isWrittenBy(1L)).isTrue();
        assertThat(comment().isWrittenBy(2L)).isFalse();
    }
}
