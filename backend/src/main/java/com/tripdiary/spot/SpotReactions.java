package com.tripdiary.spot;

/**
 * スポットへの反応（行ってみたい！の件数と、閲覧者が付けているか）。
 *
 * @param likeCount 行ってみたい！の件数
 * @param likedByMe 閲覧者が付けているか（未ログインなら false）
 */
public record SpotReactions(long likeCount, boolean likedByMe) {

    public static final SpotReactions NONE = new SpotReactions(0, false);
}
