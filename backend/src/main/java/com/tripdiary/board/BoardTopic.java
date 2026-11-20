package com.tripdiary.board;

import com.tripdiary.community.Community;
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
 * 掲示板のトピック（テーブル定義: docs/database_schema.md 3.11）。
 *
 * <p>日時はアプリの時計で入れ、DB の精度（マイクロ秒）に切り捨てる（レスポンスの値と保存される値をそろえ、カーソルがずれないようにする）。
 */
@Entity
@Table(name = "board_topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** コミュニティ内のトピック（内輪トーク）ならそのコミュニティ。広場のトピックは null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", updatable = false)
    private Community community;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 広場のトピック。 */
    public BoardTopic(User user, String title, String body, Instant now) {
        this(null, user, title, body, now);
    }

    /**
     * @param community コミュニティ内のトピックならそのコミュニティ（広場は null）
     */
    public BoardTopic(Community community, User user, String title, String body, Instant now) {
        this.community = community;
        this.user = user;
        this.title = title;
        this.body = body;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.lastActivityAt = this.createdAt;
    }

    /** 返信があったことを記録する（一覧の並び順を上げる）。時計が戻っても後退させない。 */
    public void touch(Instant now) {
        Instant truncated = now.truncatedTo(ChronoUnit.MICROS);
        if (truncated.isAfter(lastActivityAt)) {
            this.lastActivityAt = truncated;
        }
    }

    public boolean isWrittenBy(long userId) {
        return user.getId().equals(userId);
    }

    /** コミュニティのオーナーか（コミュニティ内のトピック・返信を削除できる）。広場のトピックでは常に false。 */
    public boolean isModeratedBy(long userId) {
        return community != null && community.isOwnedBy(userId);
    }

    @Override
    public String toString() {
        return "BoardTopic{id=" + id + "}";
    }
}
