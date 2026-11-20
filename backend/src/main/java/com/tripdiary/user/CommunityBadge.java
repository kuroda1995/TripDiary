package com.tripdiary.user;

/**
 * コミュニティ称号（docs/features/11_community_reactions.md 5章）。投稿・コメント・掲示板の書き込みの合計で決まる。
 *
 * <p>宣言順は下位から。表示名は画面側で持つ。
 */
public enum CommunityBadge {
    /** 参拝ビギナー（1件以上） */
    BEGINNER(1),
    /** 御朱印巡り人（3件以上） */
    PILGRIM(3),
    /** お寺マイスター（5件以上） */
    MASTER(5);

    private final int threshold;

    CommunityBadge(int threshold) {
        this.threshold = threshold;
    }

    public int threshold() {
        return threshold;
    }

    /** 活動数に応じた称号（0件なら null）。 */
    public static CommunityBadge of(long activityCount) {
        CommunityBadge badge = null;
        for (CommunityBadge candidate : values()) {
            if (activityCount >= candidate.threshold) {
                badge = candidate;
            }
        }
        return badge;
    }
}
