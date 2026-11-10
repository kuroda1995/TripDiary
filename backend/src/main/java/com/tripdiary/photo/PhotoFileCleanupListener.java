package com.tripdiary.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 不要になった写真ファイルを、DB のコミット後に非同期で S3 から削除する（docs/features/02_spot.md 5章）。
 *
 * <p>削除に失敗してもユーザーの操作は成功させる。消し残したファイルは {@link OrphanPhotoCleaner} が後で削除する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoFileCleanupListener {

    private final PhotoStorage photoStorage;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiscarded(PhotoFilesDiscardedEvent event) {
        if (event.s3Keys().isEmpty()) {
            return;
        }
        try {
            int failed = photoStorage.deleteAll(event.s3Keys());
            if (failed > 0) {
                log.warn("Some photo files could not be deleted failed={}", failed);
            } else {
                log.info("Photo files deleted count={}", event.s3Keys().size());
            }
        } catch (StorageException e) {
            log.warn("Photo files could not be deleted count={}", event.s3Keys().size(), e);
        }
    }
}
