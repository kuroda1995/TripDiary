package com.tripdiary.photo;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoFileCleanupListenerTest {

    private final PhotoStorage photoStorage = mock(PhotoStorage.class);
    private final PhotoFileCleanupListener listener = new PhotoFileCleanupListener(photoStorage);

    @Test
    @DisplayName("通知されたファイルを S3 から削除する")
    void deletesFiles() {
        listener.onDiscarded(
                new PhotoFilesDiscardedEvent(List.of("spots/1/a.jpg", "spots/1/b.jpg")));

        verify(photoStorage).deleteAll(List.of("spots/1/a.jpg", "spots/1/b.jpg"));
    }

    @Test
    @DisplayName("キーが空なら S3 を呼ばない")
    void ignoresEmptyEvent() {
        listener.onDiscarded(new PhotoFilesDiscardedEvent(List.of()));

        verify(photoStorage, never()).deleteAll(any());
    }

    @Test
    @DisplayName("S3 の障害でも例外を外に出さない（消し残しは孤立ファイル削除で片付ける）")
    void swallowsStorageErrors() {
        when(photoStorage.deleteAll(any())).thenThrow(new StorageException(new RuntimeException()));

        assertThatCode(() -> listener.onDiscarded(new PhotoFilesDiscardedEvent(List.of("k"))))
                .doesNotThrowAnyException();
    }
}
