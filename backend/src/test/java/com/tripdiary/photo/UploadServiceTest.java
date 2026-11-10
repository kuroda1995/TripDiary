package com.tripdiary.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.photo.PhotoStorage.PresignedUpload;
import com.tripdiary.photo.dto.UploadUrlRequest;
import com.tripdiary.photo.dto.UploadUrlResponse;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UploadServiceTest {

    private static final long MB = 1024 * 1024;

    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final UploadService service = new UploadService(photoStorage);

    @BeforeEach
    void setUp() {
        when(photoStorage.presignPut(anyString(), anyString(), anyLong()))
                .thenReturn(
                        new PresignedUpload(
                                "https://s3.example.com/signed",
                                Instant.parse("2026-11-11T03:20:42Z")));
    }

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, jpg",
        "image/png, png",
        "image/webp, webp",
        "image/heic, heic",
    })
    @DisplayName("対応形式なら spots/{userId}/{UUID}.{拡張子} のキーで署名付き URL を発行する")
    void issuesUrlForSupportedFormats(String contentType, String extension) {
        UploadUrlResponse response =
                service.issueUploadUrl(
                        42L, new UploadUrlRequest(UploadPurpose.SPOT_PHOTO, contentType, 1000L));

        assertThat(response.s3Key())
                .matches(
                        "spots/42/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
                                + extension);
        assertThat(response.uploadUrl()).isEqualTo("https://s3.example.com/signed");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-11-11T03:20:42Z"));
        // Content-Type とサイズを署名の条件にする
        verify(photoStorage).presignPut(response.s3Key(), contentType, 1000L);
        assertThat(UploadPurpose.SPOT_PHOTO.isOwnKey(response.s3Key(), 42L)).isTrue();
    }

    @Test
    @DisplayName("キーは毎回異なる（ファイル名にユーザー入力を使わない）")
    void keysAreUnique() {
        var request = new UploadUrlRequest(UploadPurpose.SPOT_PHOTO, "image/jpeg", 1000L);

        assertThat(service.issueUploadUrl(1L, request).s3Key())
                .isNotEqualTo(service.issueUploadUrl(1L, request).s3Key());
    }

    @Test
    @DisplayName("アイコンは avatars/{userId}/ に置く")
    void avatarPrefix() {
        UploadUrlResponse response =
                service.issueUploadUrl(
                        7L, new UploadUrlRequest(UploadPurpose.AVATAR, "image/png", 1000L));

        assertThat(response.s3Key()).startsWith("avatars/7/").endsWith(".png");
        assertThat(UploadPurpose.AVATAR.isOwnKey(response.s3Key(), 7L)).isTrue();
        assertThat(UploadPurpose.SPOT_PHOTO.isOwnKey(response.s3Key(), 7L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"image/gif", "image/svg+xml", "application/pdf", "image/jpg", "IMAGE/JPEG"})
    @DisplayName("対応外の形式は UNSUPPORTED_CONTENT_TYPE")
    void rejectsUnsupportedContentType(String contentType) {
        assertThatThrownBy(
                        () ->
                                service.issueUploadUrl(
                                        1L,
                                        new UploadUrlRequest(
                                                UploadPurpose.SPOT_PHOTO, contentType, 1000L)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(UploadService.UNSUPPORTED_CONTENT_TYPE);
        verify(photoStorage, never()).presignPut(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("サイズ上限: スポット写真は 10MB、アイコンは 5MB まで（上限ちょうどは可）")
    void sizeLimits() {
        service.issueUploadUrl(
                1L, new UploadUrlRequest(UploadPurpose.SPOT_PHOTO, "image/jpeg", 10 * MB));
        service.issueUploadUrl(
                1L, new UploadUrlRequest(UploadPurpose.AVATAR, "image/jpeg", 5 * MB));

        assertThatThrownBy(
                        () ->
                                service.issueUploadUrl(
                                        1L,
                                        new UploadUrlRequest(
                                                UploadPurpose.SPOT_PHOTO,
                                                "image/jpeg",
                                                10 * MB + 1)))
                .extracting("code")
                .isEqualTo(UploadService.FILE_TOO_LARGE);
        assertThatThrownBy(
                        () ->
                                service.issueUploadUrl(
                                        1L,
                                        new UploadUrlRequest(
                                                UploadPurpose.AVATAR, "image/jpeg", 5 * MB + 1)))
                .extracting("code")
                .isEqualTo(UploadService.FILE_TOO_LARGE);
        verify(photoStorage, org.mockito.Mockito.times(2))
                .presignPut(anyString(), eq("image/jpeg"), anyLong());
    }
}
