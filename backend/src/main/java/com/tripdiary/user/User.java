package com.tripdiary.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** ユーザー（テーブル定義: docs/database_schema.md 3.1）。 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String bio;

    @Column(name = "avatar_s3_key")
    private String avatarS3Key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String email, String username, String passwordHash, String displayName) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void changeBio(String bio) {
        this.bio = bio;
    }

    /**
     * アイコンを差し替える。
     *
     * @param avatarS3Key 新しいアイコンの S3 キー（null で削除）
     * @return 差し替え前のキー（なければ null）
     */
    public String changeAvatar(String avatarS3Key) {
        String previous = this.avatarS3Key;
        this.avatarS3Key = avatarS3Key;
        return previous;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** パスワードハッシュをログ等に出さないよう toString は ID とユーザー名のみ。 */
    @Override
    public String toString() {
        return "User{id=" + id + ", username=" + username + "}";
    }
}
