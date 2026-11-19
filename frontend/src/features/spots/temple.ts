/** お寺の5段階評価と特徴タグ（docs/features/09_temple_review.md）。 */

export type TempleFeature = 'GOSHUIN' | 'PARKING' | 'GARDEN' | 'CULTURAL_PROPERTY' | 'QUIET'

/** 表示順（サーバーの並びと同じ）。 */
export const TEMPLE_FEATURES: TempleFeature[] = ['GOSHUIN', 'PARKING', 'GARDEN', 'CULTURAL_PROPERTY', 'QUIET']

export const TEMPLE_FEATURE_LABELS: Record<TempleFeature, string> = {
  GOSHUIN: '御朱印あり',
  PARKING: '駐車場あり',
  GARDEN: '庭園が美しい',
  CULTURAL_PROPERTY: '国宝・重要文化財',
  QUIET: '静かに過ごせる',
}

export function isTempleFeature(value: string): value is TempleFeature {
  return (TEMPLE_FEATURES as string[]).includes(value)
}

/** 各項目 1〜5、未評価は null。 */
export type TempleRatings = {
  tranquility: number | null
  beauty: number | null
  worship: number | null
  access: number | null
}

export type RatingKey = keyof TempleRatings

export const RATING_ITEMS: { key: RatingKey; label: string }[] = [
  { key: 'tranquility', label: '静寂さ・雰囲気' },
  { key: 'beauty', label: '庭園・建築の美しさ' },
  { key: 'worship', label: '御朱印・参拝の充実度' },
  { key: 'access', label: 'アクセス・駐車場の停めやすさ' },
]

export const MAX_RATING = 5

export const emptyRatings: TempleRatings = { tranquility: null, beauty: null, worship: null, access: null }

/** 評価した項目の平均（小数第1位まで）。1項目も評価がなければ null。 */
export function averageRating(ratings: TempleRatings | null): number | null {
  if (!ratings) return null
  const values = RATING_ITEMS.map(({ key }) => ratings[key]).filter((v): v is number => v !== null)
  if (values.length === 0) return null
  return Math.round((values.reduce((sum, v) => sum + v, 0) / values.length) * 10) / 10
}
