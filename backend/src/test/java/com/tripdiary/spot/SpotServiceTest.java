package com.tripdiary.spot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.area.AreaTag;
import com.tripdiary.area.AreaTagService;
import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.spot.Spot.SpotContent;
import com.tripdiary.spot.dto.SpotRequest;
import com.tripdiary.support.MutableClock;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SpotServiceTest {

    private final SpotRepository spotRepository = mock(SpotRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AreaTagService areaTagService = mock(AreaTagService.class);
    // 2026-11-11T15:30Z は日本時間で 2026-11-12 00:30
    private final MutableClock clock = MutableClock.at("2026-11-11T15:30:00Z");
    private final SpotService service =
            new SpotService(spotRepository, userRepository, areaTagService, clock);

    private final User alice = TestUsers.user(1L, "alice");
    private final User bob = TestUsers.user(2L, "bob");

    @BeforeEach
    void setUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(areaTagService.getAllByIds(any())).thenReturn(Set.of());
        when(spotRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
    }

    private static Spot withId(Spot spot, long id) {
        ReflectionTestUtils.setField(spot, "id", id);
        return spot;
    }

    private static SpotRequest request(LocalDate visitedOn, List<Long> areaTagIds) {
        return new SpotRequest(
                "  朝の清水寺  ", " 清水寺 ", areaTagIds, "  ", visitedOn, Visibility.PUBLIC);
    }

    private Spot spotOf(User owner, Visibility visibility) {
        Spot spot =
                new Spot(
                        owner,
                        new SpotContent("t", "l", null, null, visibility, Set.<AreaTag>of()));
        withId(spot, 10L);
        when(spotRepository.findWithDetailsById(10L)).thenReturn(Optional.of(spot));
        return spot;
    }

    @Test
    @DisplayName("作成: タイトル・場所の前後の空白を除き、空白だけの感想は null として保存する")
    void createNormalizesInput() {
        service.create(1L, request(null, List.of(29L)));

        ArgumentCaptor<Spot> saved = ArgumentCaptor.forClass(Spot.class);
        verify(spotRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("朝の清水寺");
        assertThat(saved.getValue().getLocationName()).isEqualTo("清水寺");
        assertThat(saved.getValue().getImpression()).isNull();
        assertThat(saved.getValue().getUser()).isSameAs(alice);
    }

    @Test
    @DisplayName("作成: 同じエリアタグが重複していたら DUPLICATE_AREA_TAG")
    void createRejectsDuplicateAreaTags() {
        assertBadRequest(
                () -> service.create(1L, request(null, List.of(29L, 29L))),
                SpotService.DUPLICATE_AREA_TAG);
        verify(spotRepository, never()).save(any());
    }

    @Test
    @DisplayName("作成: 訪問日は日本時間の今日までなら可、明日以降は VISITED_ON_IN_FUTURE")
    void visitedOnMustNotBeInFutureInJapanTime() {
        // UTC ではまだ 11/11 だが、日本時間では 11/12 なので 11/12 は「今日」として許可する
        service.create(1L, request(LocalDate.parse("2026-11-12"), List.of(29L)));

        assertBadRequest(
                () -> service.create(1L, request(LocalDate.parse("2026-11-13"), List.of(29L))),
                SpotService.VISITED_ON_IN_FUTURE);
    }

    @Test
    @DisplayName("詳細: 他人の非公開スポットは 404（存在を知られないため 403 にしない）")
    void privateSpotOfOthersIsNotFound() {
        spotOf(alice, Visibility.PRIVATE);

        assertThatThrownBy(() -> service.get(10L, 2L))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo(SpotService.SPOT_NOT_FOUND);
        assertThatThrownBy(() -> service.get(10L, null)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(10L, 1L).id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("更新・削除: 他人の公開スポットは 403、他人の非公開スポットは 404")
    void onlyOwnerCanEditOrDelete() {
        spotOf(alice, Visibility.PUBLIC);
        assertThatThrownBy(() -> service.update(10L, 2L, request(null, List.of(29L))))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(10L, 2L)).isInstanceOf(ForbiddenException.class);

        spotOf(alice, Visibility.PRIVATE);
        assertThatThrownBy(() -> service.delete(10L, 2L)).isInstanceOf(NotFoundException.class);
        verify(spotRepository, never()).delete(any());
    }

    @Test
    @DisplayName("更新: 本人なら内容を置き換える")
    void ownerCanUpdate() {
        Spot spot = spotOf(alice, Visibility.PRIVATE);

        service.update(10L, 1L, request(null, List.of(29L)));

        assertThat(spot.getTitle()).isEqualTo("朝の清水寺");
        assertThat(spot.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    @DisplayName("ユーザー別一覧: 存在しないユーザーは USER_NOT_FOUND")
    void listByUnknownUser() {
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByUser("nobody", null, null, 20))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo(SpotService.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("ユーザー別一覧: 本人が見る場合だけ非公開も含める")
    void listByUserIncludesPrivateOnlyForOwner() {
        when(userRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(bob));

        service.listByUser("bob", 2L, null, 20);
        service.listByUser("bob", 1L, null, 20);
        service.listByUser("bob", null, null, 20);

        verify(spotRepository)
                .findByUserFirstPage(
                        org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.eq(true),
                        any());
        verify(spotRepository, org.mockito.Mockito.times(2))
                .findByUserFirstPage(
                        org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.eq(false),
                        any());
    }

    private static void assertBadRequest(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
