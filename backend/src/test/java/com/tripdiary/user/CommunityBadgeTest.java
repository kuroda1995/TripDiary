package com.tripdiary.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunityBadgeTest {

    @Test
    @DisplayName("活動数の境界: 0件は称号なし、1〜2件 BEGINNER、3〜4件 PILGRIM、5件以上 MASTER")
    void thresholds() {
        assertThat(CommunityBadge.of(0)).isNull();
        assertThat(CommunityBadge.of(1)).isEqualTo(CommunityBadge.BEGINNER);
        assertThat(CommunityBadge.of(2)).isEqualTo(CommunityBadge.BEGINNER);
        assertThat(CommunityBadge.of(3)).isEqualTo(CommunityBadge.PILGRIM);
        assertThat(CommunityBadge.of(4)).isEqualTo(CommunityBadge.PILGRIM);
        assertThat(CommunityBadge.of(5)).isEqualTo(CommunityBadge.MASTER);
        assertThat(CommunityBadge.of(100)).isEqualTo(CommunityBadge.MASTER);
    }
}
