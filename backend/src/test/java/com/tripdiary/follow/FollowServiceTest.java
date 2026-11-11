package com.tripdiary.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.ApiException;
import com.tripdiary.follow.FollowRepository.FollowRow;
import com.tripdiary.follow.FollowRepository.Position;
import com.tripdiary.follow.FollowRepository.TimelineEntry;
import com.tripdiary.follow.dto.FollowResponse;
import com.tripdiary.follow.dto.FollowUserPageResponse;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.spot.SpotService;
import com.tripdiary.spot.dto.SpotPageResponse;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FollowServiceTest {

    private static final Instant T = Instant.parse("2026-11-01T00:00:00Z");

    private final FollowRepository followRepository = mock(FollowRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SpotService spotService = mock(SpotService.class);
    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final FollowService service =
            new FollowService(followRepository, userRepository, spotService, photoStorage);

    @BeforeEach
    void setUp() {
        when(userRepository.findByUsernameIgnoreCase("alice"))
                .thenReturn(Optional.of(TestUsers.user(1L, "alice")));
        when(userRepository.findByUsernameIgnoreCase("bob"))
                .thenReturn(Optional.of(TestUsers.user(2L, "bob")));
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
        when(photoStorage.publicUrl(any()))
                .thenAnswer(inv -> "https://img.test/" + inv.getArgument(0));
    }

    @Test
    @DisplayName("フォローすると相手の ID で登録し、相手のユーザー名とフォロワー数を返す")
    void follow() {
        when(followRepository.countFollowers(2L)).thenReturn(5L);

        FollowResponse response = service.follow("bob", 1L);

        verify(followRepository).insertIfAbsent(1L, 2L);
        assertThat(response).isEqualTo(new FollowResponse("bob", true, 5));
    }

    @Test
    @DisplayName("解除すると following=false を返す")
    void unfollow() {
        when(followRepository.countFollowers(2L)).thenReturn(4L);

        assertThat(service.unfollow("bob", 1L)).isEqualTo(new FollowResponse("bob", false, 4));
        verify(followRepository).delete(1L, 2L);
    }

    @Test
    @DisplayName("自分自身は 400 CANNOT_FOLLOW_SELF で、DB には触れない")
    void cannotFollowSelf() {
        assertThatThrownBy(() -> service.follow("alice", 1L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(e.getCode()).isEqualTo(FollowService.CANNOT_FOLLOW_SELF);
                        });
        assertThatThrownBy(() -> service.unfollow("alice", 1L)).isInstanceOf(ApiException.class);
        verify(followRepository, never()).insertIfAbsent(anyLong(), anyLong());
        verify(followRepository, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("存在しないユーザーは 404 USER_NOT_FOUND")
    void unknownUser() {
        assertThatThrownBy(() -> service.follow("nobody", 1L))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(FollowService.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.followers("nobody", null, null, 20))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("一覧は limit + 1 件取得し、超えたら最後の行のフォロー日時とユーザー ID をカーソルにする")
    void listPaging() {
        List<FollowRow> rows =
                List.of(
                        new FollowRow(
                                3L, "carol", "キャロル", "avatars/3/a.webp", T.plusSeconds(2), true),
                        new FollowRow(4L, "dave", "デイブ", null, T.plusSeconds(1), false),
                        new FollowRow(5L, "eve", "イブ", null, T, false));
        when(followRepository.findFollowing(eq(1L), eq(2L), isNull(), eq(3))).thenReturn(rows);

        FollowUserPageResponse page = service.following("alice", 2L, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).avatarUrl()).isEqualTo("https://img.test/avatars/3/a.webp");
        assertThat(page.items().get(0).followedByMe()).isTrue();
        assertThat(page.items().get(1).avatarUrl()).isNull();
        assertThat(SpotCursor.decode(page.nextCursor()))
                .isEqualTo(new SpotCursor(T.plusSeconds(1), 4L));
    }

    @Test
    @DisplayName("カーソルを渡すと、その位置より後を取得する。最後のページはカーソルなし")
    void listWithCursor() {
        String cursor = new SpotCursor(T, 4L).encode();
        when(followRepository.findFollowers(1L, null, new Position(T, 4L), 21))
                .thenReturn(List.of(new FollowRow(5L, "eve", "イブ", null, T, false)));

        FollowUserPageResponse page = service.followers("alice", null, cursor, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("タイムラインは取得した順のままスポットを変換し、次ページのカーソルを返す")
    void timeline() {
        when(followRepository.findTimeline(eq(1L), isNull(), anyInt()))
                .thenReturn(
                        List.of(
                                new TimelineEntry(30L, T.plusSeconds(2)),
                                new TimelineEntry(20L, T.plusSeconds(1)),
                                new TimelineEntry(10L, T)));
        when(spotService.summariesInOrder(List.of(30L, 20L), 1L)).thenReturn(List.of());

        SpotPageResponse page = service.timeline(1L, null, 2);

        verify(spotService).summariesInOrder(List.of(30L, 20L), 1L);
        assertThat(SpotCursor.decode(page.nextCursor()))
                .isEqualTo(new SpotCursor(T.plusSeconds(1), 20L));
    }
}
