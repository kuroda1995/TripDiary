package com.tripdiary.spot;

/**
 * お寺の特徴タグ（docs/features/09_temple_review.md 5章）。
 *
 * <p>DB の {@code spots.temple_features} には名前（{@code GOSHUIN} など）で保存する。値を追加するときは V6 の CHECK
 * 制約も新しいマイグレーションで変えること。表示順はこの宣言順。
 */
public enum TempleFeature {
    /** 御朱印あり */
    GOSHUIN,
    /** 駐車場あり */
    PARKING,
    /** 庭園が美しい */
    GARDEN,
    /** 国宝・重要文化財 */
    CULTURAL_PROPERTY,
    /** 静かに過ごせる */
    QUIET
}
