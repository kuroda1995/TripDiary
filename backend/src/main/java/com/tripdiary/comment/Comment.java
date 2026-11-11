package com.tripdiary.comment;

import com.tripdiary.spot.Spot;
import com.tripdiary.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * スポットへのコメント（テーブル定義: docs/database_schema.md 3.8）。
 *
 * <p>「編集済み」を {@code updatedAt != createdAt} で判定するため、日時は DB の自動設定に任せず、 作成時に同じ値を入れ、本文が変わったときだけ {@code
 * updatedAt} を進める。 DB（TIMESTAMPTZ）はマイクロ秒までのため、保存前後で値が変わらないよう切り捨てておく。
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false, updatable = false)
    private Spot spot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Comment(Spot spot, User user, String body, Instant now) {
        this.spot = spot;
        this.user = user;
        this.body = body;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    /** 本文を変える。同じ本文なら何もしない（「編集済み」にしない）。 */
    public void edit(String body, Instant now) {
        if (this.body.equals(body)) {
            return;
        }
        this.body = body;
        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        // 作成と同じマイクロ秒に編集されても「編集済み」と分かるようにする
        this.updatedAt =
                truncated.isAfter(createdAt) ? truncated : createdAt.plus(1, ChronoUnit.MICROS);
    }

    public boolean isEdited() {
        return !updatedAt.equals(createdAt);
    }

    public boolean isWrittenBy(long userId) {
        return user.getId().equals(userId);
    }

    @Override
    public String toString() {
        return "Comment{id=" + id + "}";
    }
}
