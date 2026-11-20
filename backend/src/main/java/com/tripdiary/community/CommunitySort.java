package com.tripdiary.community;

/** コミュニティ一覧の並び順。 */
public enum CommunitySort {
    /** 新着順（作成日時の新しい順） */
    NEWEST,
    /** 人気順（参加メンバー数の多い順。同数は新しい順） */
    POPULAR;

    /** クエリパラメータ（newest / popular）から変換する。値はコントローラーで検証済み。 */
    public static CommunitySort of(String value) {
        return "popular".equals(value) ? POPULAR : NEWEST;
    }
}
