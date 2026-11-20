package com.tripdiary.community;

/**
 * コミュニティのカテゴリタグ（docs/features/12_communities.md 5章）。
 *
 * <p>DB の {@code communities.categories} には名前（{@code GOSHUIN} など）で保存する。値を追加するときは V9 の CHECK
 * 制約も新しいマイグレーションで変えること。表示順はこの宣言順。
 */
public enum CommunityCategory {
    /** 地域別 */
    REGIONAL,
    /** 御朱印 */
    GOSHUIN,
    /** 建築・庭園 */
    ARCHITECTURE_GARDEN,
    /** 初心者歓迎 */
    BEGINNER_FRIENDLY,
    /** 写真・撮影 */
    PHOTOGRAPHY
}
