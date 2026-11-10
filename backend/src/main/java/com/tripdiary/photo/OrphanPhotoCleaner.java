package com.tripdiary.photo;

import com.tripdiary.photo.PhotoStorage.ListedObject;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 孤立した写真ファイルの削除（PHOTO-06）。
 *
 * <p>アップロードしたまま投稿されなかったファイルや、削除の消し残しを片付ける。 保持期間（既定24時間）より古く、どこからも参照されていないファイルを削除する。
 * 投稿中のユーザーのファイルを消さないよう、新しいファイルは対象外にする。
 *
 * <p>参照の確認は用途ごとのテーブル（スポット写真: {@code spot_photos}、アイコン: {@code users.avatar_s3_key}）に対して行う。
 * 複数のサーバーで同時に動いても、削除は冪等なので問題ない。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanPhotoCleaner {

    /** 用途ごとの「使用中のキー」を調べる SQL。 */
    private static final Map<UploadPurpose, String> USED_KEYS_SQL =
            Map.of(
                    UploadPurpose.SPOT_PHOTO,
                    "SELECT s3_key FROM spot_photos WHERE s3_key IN (:keys)",
                    UploadPurpose.AVATAR,
                    "SELECT avatar_s3_key FROM users WHERE avatar_s3_key IN (:keys)");

    private final PhotoStorage photoStorage;
    private final NamedParameterJdbcTemplate jdbc;
    private final StorageProperties properties;
    private final Clock clock;

    /**
     * 孤立ファイルを削除する。
     *
     * @return 削除したファイル数
     */
    public int cleanUp() {
        Instant cutoff = clock.instant().minus(properties.orphanRetention());
        int deleted = 0;
        for (UploadPurpose purpose : UploadPurpose.values()) {
            deleted += cleanUp(purpose, cutoff);
        }
        log.info("Orphan photo cleanup finished deleted={}", deleted);
        return deleted;
    }

    private int cleanUp(UploadPurpose purpose, Instant cutoff) {
        AtomicInteger deleted = new AtomicInteger();
        photoStorage.listPages(
                purpose.prefix() + "/",
                page -> {
                    List<String> oldKeys =
                            page.stream()
                                    .filter(o -> o.lastModified().isBefore(cutoff))
                                    .map(ListedObject::key)
                                    .toList();
                    if (oldKeys.isEmpty()) {
                        return;
                    }
                    Set<String> used =
                            new HashSet<>(
                                    jdbc.queryForList(
                                            USED_KEYS_SQL.get(purpose),
                                            Map.of("keys", oldKeys),
                                            String.class));
                    List<String> orphans = oldKeys.stream().filter(k -> !used.contains(k)).toList();
                    if (!orphans.isEmpty()) {
                        int failed = photoStorage.deleteAll(orphans);
                        deleted.addAndGet(orphans.size() - failed);
                    }
                });
        return deleted.get();
    }
}
