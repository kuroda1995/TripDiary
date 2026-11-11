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
import com.tripdiary.photo.PhotoFilesDiscardedEvent;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.photo.PhotoStorage.StoredObject;
import com.tripdiary.spot.Spot.NewPhoto;
import com.tripdiary.spot.Spot.SpotContent;
import com.tripdiary.spot.dto.PhotoRequest;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class SpotServiceTest {

    private final SpotRepository spotRepository = mock(SpotRepository.class);
    private final SpotPhotoRepository spotPhotoRepository = mock(SpotPhotoRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AreaTagService areaTagService = mock(AreaTagService.class);
    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpotReactionReader reactionReader = mock(SpotReactionReader.class);
    private final SpotSearchRepository spotSearchRepository = mock(SpotSearchRepository.class);
    // 2026-11-11T15:30Z は日本時間で 2026-11-12 00:30
    private final MutableClock clock = MutableClock.at("2026-11-11T15:30:00Z");
    private final SpotService service =
            new SpotService(
                    spotRepository,
                    spotPhotoRepository,
                    userRepository,
                    areaTagService,
                    photoStorage,
                    eventPublisher,
                    reactionReader,
                    spotSearchRepository,
                    clock);

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
                "  朝の清水寺  ", " 清水寺 ", areaTagIds, "  ", visitedOn, Visibility.PUBLIC, null);
    }

    private static SpotRequest requestWithPhotos(PhotoRequest... photos) {
        return new SpotRequest(
                "朝の清水寺", "清水寺", List.of(29L), null, null, Visibility.PUBLIC, List.of(photos));
    }

    /** alice（ID=1）の写真キー。 */
    private static String aliceKey(int n) {
        return "spots/1/00000000-0000-4000-8000-%012d.jpg".formatted(n);
    }

    /** S3 上に JPEG として存在する扱いにする。 */
    private void stored(String... keys) {
        for (String key : keys) {
            when(photoStorage.head(key))
                    .thenReturn(Optional.of(new StoredObject("image/jpeg", 1024)));
        }
    }

    private Spot spotWithPhotos(String... keys) {
        Spot spot = spotOf(alice, Visibility.PUBLIC);
        stored(keys);
        spot.replacePhotos(
                java.util.Arrays.stream(keys).map(k -> new NewPhoto(k, "image/jpeg")).toList());
        org.mockito.Mockito.clearInvocations(photoStorage);
        return spot;
    }

    private List<String> photoKeysInOrder(Spot spot) {
        return spot.getPhotos().stream()
                .sorted(java.util.Comparator.comparingInt(SpotPhoto::getSortOrder))
                .map(SpotPhoto::getS3Key)
                .toList();
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

    @Nested
    @DisplayName("写真")
    class Photos {

        @Test
        @DisplayName("作成: sortOrder の順に並べ、形式は S3 上のファイルの Content-Type を保存する")
        void createWithPhotos() {
            stored(aliceKey(1));
            when(photoStorage.head(aliceKey(2)))
                    .thenReturn(Optional.of(new StoredObject("image/png", 2048)));

            service.create(
                    1L,
                    requestWithPhotos(
                            new PhotoRequest(aliceKey(1), 5), new PhotoRequest(aliceKey(2), 0)));

            ArgumentCaptor<Spot> saved = ArgumentCaptor.forClass(Spot.class);
            verify(spotRepository).save(saved.capture());
            List<SpotPhoto> photos = saved.getValue().getPhotos();
            assertThat(photos)
                    .extracting(
                            SpotPhoto::getS3Key, SpotPhoto::getSortOrder, SpotPhoto::getContentType)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(aliceKey(2), 0, "image/png"),
                            org.assertj.core.groups.Tuple.tuple(aliceKey(1), 1, "image/jpeg"));
        }

        @Test
        @DisplayName("11枚以上は TOO_MANY_PHOTOS")
        void tooManyPhotos() {
            PhotoRequest[] photos = new PhotoRequest[11];
            for (int i = 0; i < photos.length; i++) {
                photos[i] = new PhotoRequest(aliceKey(i), i % 10);
            }
            assertBadRequest(
                    () -> service.create(1L, requestWithPhotos(photos)),
                    SpotService.TOO_MANY_PHOTOS);
        }

        @Test
        @DisplayName("並び順の重複は DUPLICATE_PHOTO_ORDER、同じキーの重複は INVALID_PHOTO_KEY")
        void duplicates() {
            stored(aliceKey(1), aliceKey(2));
            assertBadRequest(
                    () ->
                            service.create(
                                    1L,
                                    requestWithPhotos(
                                            new PhotoRequest(aliceKey(1), 0),
                                            new PhotoRequest(aliceKey(2), 0))),
                    SpotService.DUPLICATE_PHOTO_ORDER);
            assertBadRequest(
                    () ->
                            service.create(
                                    1L,
                                    requestWithPhotos(
                                            new PhotoRequest(aliceKey(1), 0),
                                            new PhotoRequest(aliceKey(1), 1))),
                    SpotService.INVALID_PHOTO_KEY);
        }

        @Test
        @DisplayName("他人のキー・アイコン用のキー・不正な形式のキーは INVALID_PHOTO_KEY（S3 に問い合わせない）")
        void keyMustBeOwnSpotPhotoKey() {
            for (String key :
                    List.of(
                            "spots/2/00000000-0000-4000-8000-000000000001.jpg",
                            "avatars/1/00000000-0000-4000-8000-000000000001.jpg",
                            "spots/1/../2/00000000-0000-4000-8000-000000000001.jpg",
                            "spots/1/my-photo.jpg",
                            "spots/1/00000000-0000-4000-8000-000000000001.exe")) {
                assertBadRequest(
                        () -> service.create(1L, requestWithPhotos(new PhotoRequest(key, 0))),
                        SpotService.INVALID_PHOTO_KEY);
            }
            verify(photoStorage, never()).head(any());
            verify(spotRepository, never()).save(any());
        }

        @Test
        @DisplayName("他の投稿で使用済みのキーは INVALID_PHOTO_KEY")
        void keyAlreadyUsed() {
            stored(aliceKey(1));
            when(spotPhotoRepository.existsByS3Key(aliceKey(1))).thenReturn(true);

            assertBadRequest(
                    () -> service.create(1L, requestWithPhotos(new PhotoRequest(aliceKey(1), 0))),
                    SpotService.INVALID_PHOTO_KEY);
        }

        @Test
        @DisplayName("S3 に存在しない・対応外の形式・サイズ超過のファイルは INVALID_PHOTO_KEY")
        void fileMustExistWithValidFormatAndSize() {
            when(photoStorage.head(aliceKey(1))).thenReturn(Optional.empty());
            when(photoStorage.head(aliceKey(2)))
                    .thenReturn(Optional.of(new StoredObject("image/gif", 10)));
            when(photoStorage.head(aliceKey(3)))
                    .thenReturn(Optional.of(new StoredObject("image/jpeg", 10L * 1024 * 1024 + 1)));

            for (int n = 1; n <= 3; n++) {
                String key = aliceKey(n);
                assertBadRequest(
                        () -> service.create(1L, requestWithPhotos(new PhotoRequest(key, 0))),
                        SpotService.INVALID_PHOTO_KEY);
            }
        }

        @Test
        @DisplayName("更新: 残す写真は再確認せず並び替え、外した写真はファイル削除イベントで通知する")
        void updateReplacesPhotos() {
            Spot spot = spotWithPhotos(aliceKey(1), aliceKey(2), aliceKey(3));
            stored(aliceKey(4));

            service.update(
                    10L,
                    1L,
                    requestWithPhotos(
                            new PhotoRequest(aliceKey(3), 0),
                            new PhotoRequest(aliceKey(4), 1),
                            new PhotoRequest(aliceKey(1), 2)));

            assertThat(photoKeysInOrder(spot))
                    .containsExactly(aliceKey(3), aliceKey(4), aliceKey(1));
            // 新しく付けた写真だけ S3 で確認する
            verify(photoStorage).head(aliceKey(4));
            verify(photoStorage, never()).head(aliceKey(1));
            verify(eventPublisher).publishEvent(new PhotoFilesDiscardedEvent(List.of(aliceKey(2))));
        }

        @Test
        @DisplayName("更新: 写真を外さなければファイル削除イベントは出さない")
        void updateWithoutRemovalPublishesNothing() {
            spotWithPhotos(aliceKey(1));

            service.update(10L, 1L, requestWithPhotos(new PhotoRequest(aliceKey(1), 0)));

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("削除: スポットの写真ファイルをすべて削除イベントで通知する")
        void deletePublishesAllPhotoKeys() {
            Spot spot = spotWithPhotos(aliceKey(1), aliceKey(2));

            service.delete(10L, 1L);

            verify(spotRepository).delete(spot);
            ArgumentCaptor<PhotoFilesDiscardedEvent> event =
                    ArgumentCaptor.forClass(PhotoFilesDiscardedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue().s3Keys())
                    .containsExactlyInAnyOrder(aliceKey(1), aliceKey(2));
        }
    }

    private static void assertBadRequest(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
