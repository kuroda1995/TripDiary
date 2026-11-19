package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;

import com.tripdiary.area.AreaTag;
import com.tripdiary.spot.Spot.SpotContent;
import com.tripdiary.support.TestUsers;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** お寺の評価・特徴タグの単体テスト（docs/features/09_temple_review.md 5章）。 */
class TempleReviewTest {

    private static Spot spot(TempleRatings ratings, List<TempleFeature> features) {
        return new Spot(
                TestUsers.user(1L, "alice"),
                new SpotContent(
                        "t",
                        "l",
                        null,
                        null,
                        Visibility.PUBLIC,
                        Set.<AreaTag>of(),
                        ratings,
                        features));
    }

    @Test
    @DisplayName("特徴タグは表示順（宣言順）に並べ、重複は1つにまとめる")
    void featuresAreSortedAndDistinct() {
        Spot spot =
                spot(
                        null,
                        List.of(
                                TempleFeature.QUIET,
                                TempleFeature.GOSHUIN,
                                TempleFeature.QUIET,
                                TempleFeature.GARDEN));

        assertThat(spot.templeFeatures())
                .containsExactly(TempleFeature.GOSHUIN, TempleFeature.GARDEN, TempleFeature.QUIET);
    }

    @Test
    @DisplayName("すべて未評価なら評価は null、1項目でも評価があればその値を返す")
    void ratingsAreNullWhenEmpty() {
        assertThat(spot(null, List.of()).templeRatings()).isNull();
        assertThat(spot(new TempleRatings(null, null, null, null), List.of()).templeRatings())
                .isNull();
        assertThat(spot(new TempleRatings(null, 4, null, null), List.of()).templeRatings())
                .isEqualTo(new TempleRatings(null, 4, null, null));
    }

    @Test
    @DisplayName("お寺の項目なしの投稿内容は、評価なし・タグなし")
    void contentWithoutTempleFields() {
        Spot spot =
                new Spot(
                        TestUsers.user(1L, "alice"),
                        new SpotContent("t", "l", null, null, Visibility.PUBLIC, Set.of()));

        assertThat(spot.templeRatings()).isNull();
        assertThat(spot.templeFeatures()).isEmpty();
    }
}
