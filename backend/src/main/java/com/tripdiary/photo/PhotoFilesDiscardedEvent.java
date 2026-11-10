package com.tripdiary.photo;

import java.util.List;

/**
 * 投稿の削除・写真の差し替えで不要になった写真ファイル。
 *
 * <p>トランザクションのコミット後に {@link PhotoFileCleanupListener} が S3 から削除する （ロールバック時は削除しない）。
 */
public record PhotoFilesDiscardedEvent(List<String> s3Keys) {

    public PhotoFilesDiscardedEvent {
        s3Keys = List.copyOf(s3Keys);
    }
}
