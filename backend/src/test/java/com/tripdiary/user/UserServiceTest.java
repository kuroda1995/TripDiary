package com.tripdiary.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.photo.PhotoFilesDiscardedEvent;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.photo.PhotoStorage.StoredObject;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.dto.MeResponse;
import com.tripdiary.user.dto.ProfileUpdateRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UserServiceTest {

    private static final String KEY_1 = "avatars/1/00000000-0000-4000-8000-000000000001.webp";
    private static final String KEY_2 = "avatars/1/00000000-0000-4000-8000-000000000002.png";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UserService service =
            new UserService(userRepository, photoStorage, eventPublisher);

    private final User user = TestUsers.user(1L, "taro");

    @BeforeEach
    void setUp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(photoStorage.publicUrl(any()))
                .thenAnswer(inv -> "https://img.test/" + inv.getArgument(0));
    }

    private static ProfileUpdateRequest request(
            Optional<String> displayName, Optional<String> bio, Optional<String> avatarS3Key) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDisplayName(displayName);
        request.setBio(bio);
        request.setAvatarS3Key(avatarS3Key);
        return request;
    }

    private void stored(String key, String contentType, long size) {
        when(photoStorage.head(key)).thenReturn(Optional.of(new StoredObject(contentType, size)));
    }

    @Test
    @DisplayName("送った項目だけを更新し、送らなかった項目（null）は変えない")
    void updatesOnlyGivenFields() {
        user.changeBio("元の自己紹介");

        MeResponse response =
                service.updateProfile(1L, request(Optional.of("  タロウ  "), null, null));

        assertThat(user.getDisplayName()).isEqualTo("タロウ");
        assertThat(user.getBio()).isEqualTo("元の自己紹介");
        assertThat(response.displayName()).isEqualTo("タロウ");
        assertThat(response.avatarUrl()).isNull();
    }

    @Test
    @DisplayName("自己紹介は null・空白のみで削除、それ以外は前後の空白を除いて保存する")
    void bio() {
        service.updateProfile(1L, request(null, Optional.of("  温泉めぐり  "), null));
        assertThat(user.getBio()).isEqualTo("温泉めぐり");

        service.updateProfile(1L, request(null, Optional.of(" \n "), null));
        assertThat(user.getBio()).isNull();

        user.changeBio("x");
        service.updateProfile(1L, request(null, Optional.empty(), null));
        assertThat(user.getBio()).isNull();
    }

    @Test
    @DisplayName("アイコンを設定すると配信 URL を返す（初回は削除するファイルが無い）")
    void setAvatar() {
        stored(KEY_1, "image/webp", 1000);

        MeResponse response = service.updateProfile(1L, request(null, null, Optional.of(KEY_1)));

        assertThat(user.getAvatarS3Key()).isEqualTo(KEY_1);
        assertThat(response.avatarUrl()).isEqualTo("https://img.test/" + KEY_1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("アイコンを差し替え・削除すると、古いファイルを削除イベントで通知する")
    void replaceAndRemoveAvatar() {
        user.changeAvatar(KEY_1);
        stored(KEY_2, "image/png", 1000);

        service.updateProfile(1L, request(null, null, Optional.of(KEY_2)));
        assertThat(user.getAvatarS3Key()).isEqualTo(KEY_2);
        verify(eventPublisher).publishEvent(new PhotoFilesDiscardedEvent(List.of(KEY_1)));

        service.updateProfile(1L, request(null, null, Optional.empty()));
        assertThat(user.getAvatarS3Key()).isNull();
        verify(eventPublisher).publishEvent(new PhotoFilesDiscardedEvent(List.of(KEY_2)));
    }

    @Test
    @DisplayName("同じアイコンを送り直しても何もしない（S3 に問い合わせず、削除もしない）")
    void sameAvatarIsNoop() {
        user.changeAvatar(KEY_1);

        service.updateProfile(1L, request(null, null, Optional.of(KEY_1)));

        verify(photoStorage, never()).head(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("他人のキー・スポット写真のキー・存在しない・対応外の形式・5MB 超のファイルは INVALID_AVATAR_KEY")
    void rejectsInvalidAvatar() {
        String missing = "avatars/1/00000000-0000-4000-8000-000000000003.jpg";
        String gif = "avatars/1/00000000-0000-4000-8000-000000000004.jpg";
        String large = "avatars/1/00000000-0000-4000-8000-000000000005.jpg";
        when(photoStorage.head(missing)).thenReturn(Optional.empty());
        stored(gif, "image/gif", 100);
        stored(large, "image/jpeg", 5L * 1024 * 1024 + 1);

        for (String key :
                List.of(
                        "avatars/2/00000000-0000-4000-8000-000000000001.jpg",
                        "spots/1/00000000-0000-4000-8000-000000000001.jpg",
                        missing,
                        gif,
                        large)) {
            assertThatThrownBy(
                            () -> service.updateProfile(1L, request(null, null, Optional.of(key))))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code")
                    .isEqualTo(UserService.INVALID_AVATAR_KEY);
        }
        assertThat(user.getAvatarS3Key()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
