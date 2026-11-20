package com.tripdiary.spot;

/**
 * スポットへの反応（行ってみたい！・参考になった・コメント）と、閲覧者のステータス。
 *
 * @param likeCount 行ってみたい！の件数
 * @param likedByMe 閲覧者が行ってみたい！を付けているか（未ログインなら false）
 * @param helpfulCount 参考になったの件数
 * @param helpfulByMe 閲覧者が参考になったを付けているか（未ログインなら false）
 * @param commentCount コメントの件数
 * @param myStatus 閲覧者が付けたステータス（未設定・未ログインなら null）
 */
public record SpotReactions(
        long likeCount,
        boolean likedByMe,
        long helpfulCount,
        boolean helpfulByMe,
        long commentCount,
        SpotStatus myStatus) {

    public static final SpotReactions NONE = new SpotReactions(0, false, 0, false, 0, null);
}
