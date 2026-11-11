package com.tripdiary.spot;

import java.util.Locale;

/** スポット一覧の並び順（docs/features/07_area_search.md 5章）。 */
public enum SpotSort {
    /** 投稿日時の降順 */
    NEWEST,
    /** 行ってみたい！数の降順、同数なら投稿日時の降順 */
    POPULAR;

    /** クエリパラメータの値（newest / popular）。形式はコントローラーで検証済み。 */
    public static SpotSort of(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
