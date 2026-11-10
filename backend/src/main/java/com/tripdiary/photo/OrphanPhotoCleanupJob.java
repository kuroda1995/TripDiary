package com.tripdiary.photo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 孤立した写真ファイルの定期削除（既定: 毎時）。{@code app.storage.orphan-cleanup.enabled=false} で止められる。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.storage.orphan-cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrphanPhotoCleanupJob {

    private final OrphanPhotoCleaner cleaner;

    @Scheduled(cron = "${app.storage.orphan-cleanup.cron}", zone = "UTC")
    public void run() {
        try {
            cleaner.cleanUp();
        } catch (StorageException e) {
            // 次回の実行で再試行される。続けて失敗する場合は CloudWatch の ERROR アラームで気付けるようにする
            log.error("Orphan photo cleanup failed", e);
        }
    }
}
