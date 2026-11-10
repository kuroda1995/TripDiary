package com.tripdiary.area;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** エリアタグ（マスタ。テーブル定義: docs/database_schema.md 3.5）。初期データは V2 マイグレーションで投入する。 */
@Entity
@Table(name = "area_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AreaTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AreaTag parent;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private short level;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 親タグの ID（親を読み込まずに取得する）。最上位なら null。 */
    public Long getParentId() {
        return parent == null ? null : parent.getId();
    }

    @Override
    public String toString() {
        return "AreaTag{id=" + id + ", slug=" + slug + "}";
    }
}
