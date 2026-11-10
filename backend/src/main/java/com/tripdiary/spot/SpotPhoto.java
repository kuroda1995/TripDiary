package com.tripdiary.spot;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** スポットの写真（テーブル定義: docs/database_schema.md 3.4）。sortOrder = 0 がカバー写真。 */
@Entity
@Table(name = "spot_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false, updatable = false)
    private Spot spot;

    @Column(name = "s3_key", nullable = false, updatable = false)
    private String s3Key;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "sort_order", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    SpotPhoto(Spot spot, String s3Key, String contentType, int sortOrder) {
        this.spot = spot;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.sortOrder = sortOrder;
    }

    void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "SpotPhoto{id=" + id + "}";
    }
}
