package com.tripdiary.community;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * ユーザーが作るテーマ別コミュニティ（テーブル定義: docs/database_schema.md 3.14）。
 *
 * <p>作成日時はアプリの時計で入れ、DB の精度（マイクロ秒）に切り捨てる（カーソルがずれないようにする）。
 */
@Entity
@Table(name = "communities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    /** カテゴリの名前（{@link CommunityCategory}）。読み出しは {@link #categories()} を使う。 */
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categories", nullable = false)
    private String[] categoryNames = new String[0];

    /**
     * 参加メンバー数（オーナーを含む）。
     *
     * <p>DB のトリガーで保つ（V9）ため、アプリからは書き込まない。参加・退出の直後に最新の値が必要なときは {@link
     * MembershipRepository#memberCount} で読む。
     */
    @Column(name = "member_count", nullable = false, insertable = false, updatable = false)
    private int memberCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Community(
            User owner,
            String name,
            String description,
            Collection<CommunityCategory> categories,
            Instant now) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        // 表示順（宣言順）で保存し、重複は1つにまとめる
        this.categoryNames =
                categories.stream()
                        .distinct()
                        .sorted()
                        .map(CommunityCategory::name)
                        .toArray(String[]::new);
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** カテゴリ（表示順）。 */
    public List<CommunityCategory> categories() {
        return Arrays.stream(categoryNames).map(CommunityCategory::valueOf).sorted().toList();
    }

    public boolean isOwnedBy(long userId) {
        return owner.getId().equals(userId);
    }

    @Override
    public String toString() {
        return "Community{id=" + id + "}";
    }
}
