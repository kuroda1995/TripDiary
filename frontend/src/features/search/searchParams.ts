import type { AreaTagNode } from '@/features/areas/types'
import { isTempleFeature, TEMPLE_FEATURES, type TempleFeature } from '@/features/spots/temple'

export type SpotSort = 'newest' | 'popular'

/** 画面の検索条件（URL のクエリと1対1。エリアは読みやすさのためスラッグで持つ）。 */
export type SearchCondition = {
  areas: string[]
  q: string
  /** お寺の特徴タグ（すべてを持つ投稿に絞る。表示順） */
  features: TempleFeature[]
  sort: SpotSort
}

/** API の制限（docs/features/07_area_search.md 5章）と合わせる。 */
export const MAX_AREAS = 10
export const MAX_KEYWORD_LENGTH = 100

/** `?areas=kyoto,osaka&q=温泉&features=GOSHUIN,GARDEN&sort=popular` を読む。不正な値は既定値に寄せる（知らない特徴タグは無視）。 */
export function parseSearchParams(params: URLSearchParams): SearchCondition {
  const requested = new Set(
    (params.get('features') ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(isTempleFeature),
  )
  // 重複を除き、表示順にそろえる
  const features = TEMPLE_FEATURES.filter((f) => requested.has(f))
  const areas = [
    ...new Set(
      (params.get('areas') ?? '')
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    ),
  ].slice(0, MAX_AREAS)
  const q = (params.get('q') ?? '').slice(0, MAX_KEYWORD_LENGTH)
  const sort: SpotSort = params.get('sort') === 'popular' ? 'popular' : 'newest'
  return { areas, q, features, sort }
}

/** 既定値（エリアなし・キーワードなし・特徴タグなし・新着順）は URL に出さない。 */
export function toSearchParams(condition: SearchCondition): URLSearchParams {
  const params = new URLSearchParams()
  if (condition.areas.length > 0) params.set('areas', condition.areas.join(','))
  const q = condition.q.trim()
  if (q) params.set('q', q)
  if (condition.features.length > 0) params.set('features', condition.features.join(','))
  if (condition.sort !== 'newest') params.set('sort', condition.sort)
  return params
}

/** スラッグ → タグ（地方・都道府県の両方）。 */
export function indexAreasBySlug(regions: AreaTagNode[]): Map<string, AreaTagNode> {
  const bySlug = new Map<string, AreaTagNode>()
  const visit = (tag: AreaTagNode) => {
    bySlug.set(tag.slug, tag)
    tag.children.forEach(visit)
  }
  regions.forEach(visit)
  return bySlug
}

/** タグ1つで検索する URL（スポットカード・詳細のエリアタグから使う）。 */
export function searchUrlForArea(slug: string): string {
  return `/search?${toSearchParams({ areas: [slug], q: '', features: [], sort: 'newest' })}`
}

/** お寺の特徴タグ1つで検索する URL（スポットカード・詳細の特徴タグから使う）。 */
export function searchUrlForTempleFeature(feature: TempleFeature): string {
  return `/search?${toSearchParams({ areas: [], q: '', features: [feature], sort: 'newest' })}`
}
