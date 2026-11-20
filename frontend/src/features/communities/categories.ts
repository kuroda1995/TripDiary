/** コミュニティのカテゴリタグ（docs/features/12_communities.md 5章）。 */

/** 表示順（サーバーの並びと同じ）。 */
export const COMMUNITY_CATEGORIES = [
  'REGIONAL',
  'GOSHUIN',
  'ARCHITECTURE_GARDEN',
  'BEGINNER_FRIENDLY',
  'PHOTOGRAPHY',
] as const

export type CommunityCategory = (typeof COMMUNITY_CATEGORIES)[number]

export const COMMUNITY_CATEGORY_LABELS: Record<CommunityCategory, string> = {
  REGIONAL: '地域別',
  GOSHUIN: '御朱印',
  ARCHITECTURE_GARDEN: '建築・庭園',
  BEGINNER_FRIENDLY: '初心者歓迎',
  PHOTOGRAPHY: '写真・撮影',
}

export function isCommunityCategory(value: string | null): value is CommunityCategory {
  return value !== null && (COMMUNITY_CATEGORIES as readonly string[]).includes(value)
}
