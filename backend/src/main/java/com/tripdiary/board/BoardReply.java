package com.tripdiary.board;

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

/** 掲示板のトピックへの返信（テーブル定義: docs/database_schema.md 3.12）。 */
@Entity
@Table(name = "board_replies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardReply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false, updatable = false)
    private BoardTopic topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BoardReply(BoardTopic topic, User user, String body, Instant now) {
        this.topic = topic;
        this.user = user;
        this.body = body;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isWrittenBy(long userId) {
        return user.getId().equals(userId);
    }

    @Override
    public String toString() {
        return "BoardReply{id=" + id + "}";
    }
}
