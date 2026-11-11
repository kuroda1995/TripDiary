package com.tripdiary.auth;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** リフレッシュトークン（テーブル定義: docs/database_schema.md 3.2）。トークン本体ではなく SHA-256 ハッシュを保存する。 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** ログアウト・パスワード変更・再利用検知などで失効させた日時。失効後は使えない。 */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** ローテーション（新しいトークンへの交換）で使用済みにした日時。直後の猶予期間だけは再利用を許す。 */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    /** ローテーションで使用済みにする（最初の1回の日時を保つ）。 */
    public void markRotated(Instant now) {
        if (rotatedAt == null) {
            rotatedAt = now;
        }
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    @Override
    public String toString() {
        return "RefreshToken{id=" + id + "}";
    }
}
