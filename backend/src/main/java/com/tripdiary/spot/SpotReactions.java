package com.tripdiary.spot;

/**
 * スポットへの反応（行ってみたい！・コメント）。
 *
 * @param likeCount 行ってみたい！の件数
 * @param likedByMe 閲覧者が行ってみたい！を付けているか（未ログインなら false）
 * @param commentCount コメントの件数
 */
public record SpotReactions(long likeCount, boolean likedByMe, long commentCount) {

    public static final SpotReactions NONE = new SpotReactions(0, false, 0);
}
