import type { CommunityBadge } from '@/features/spots/types'

/** 称号の表示名と条件（サーバーの CommunityBadge と同じ。docs/features/11_community_reactions.md 5章）。 */
export const BADGE_INFO: Record<CommunityBadge, { label: string; threshold: number }> = {
  BEGINNER: { label: '参拝ビギナー', threshold: 1 },
  PILGRIM: { label: '御朱印巡り人', threshold: 3 },
  MASTER: { label: 'お寺マイスター', threshold: 5 },
}
