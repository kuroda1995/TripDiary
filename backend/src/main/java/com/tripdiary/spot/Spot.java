package com.tripdiary.spot;

import com.tripdiary.area.AreaTag;
import com.tripdiary.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** スポット（旅行先の投稿。テーブル定義: docs/database_schema.md 3.3）。 */
@Entity
@Table(name = "spots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "location_name", nullable = false)
    private String locationName;

    private String impression;

    @Column(name = "visited_on")
    private LocalDate visitedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "spot_area_tags",
            joinColumns = @JoinColumn(name = "spot_id"),
            inverseJoinColumns = @JoinColumn(name = "area_tag_id"))
    private Set<AreaTag> areaTags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Spot(User user, SpotContent content) {
        this.user = user;
        apply(content);
    }

    /** 投稿内容を置き換える（編集は全項目置換。docs/features/02_spot.md 4.4）。 */
    public void update(SpotContent content) {
        apply(content);
    }

    private void apply(SpotContent content) {
        this.title = content.title();
        this.locationName = content.locationName();
        this.impression = content.impression();
        this.visitedOn = content.visitedOn();
        this.visibility = content.visibility();
        this.areaTags.clear();
        this.areaTags.addAll(content.areaTags());
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(user.getId());
    }

    /** 閲覧できるか（公開、または本人）。 */
    public boolean isVisibleTo(Long viewerId) {
        return visibility == Visibility.PUBLIC || isOwnedBy(viewerId);
    }

    @Override
    public String toString() {
        return "Spot{id=" + id + "}";
    }

    /** 投稿内容（作成・更新で共通）。 */
    public record SpotContent(
            String title,
            String locationName,
            String impression,
            LocalDate visitedOn,
            Visibility visibility,
            Set<AreaTag> areaTags) {}
}
