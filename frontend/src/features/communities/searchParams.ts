import type { CommunitySearchQuery } from './api'
import { isCommunityCategory } from './categories'

export const MAX_KEYWORD_LENGTH = 100

/** URL のクエリ（`?q=富山&category=REGIONAL&sort=popular`）から条件を読む。不正な値は無視する。 */
export function parseCommunitySearchParams(params: URLSearchParams): CommunitySearchQuery {
  const category = params.get('category')
  return {
    q: (params.get('q') ?? '').trim().slice(0, MAX_KEYWORD_LENGTH),
    category: isCommunityCategory(category) ? category : null,
    sort: params.get('sort') === 'popular' ? 'popular' : 'newest',
  }
}

/** 条件を URL のクエリにする（既定値は省く）。 */
export function toCommunitySearchParams(query: CommunitySearchQuery): URLSearchParams {
  const params = new URLSearchParams()
  if (query.q) params.set('q', query.q)
  if (query.category) params.set('category', query.category)
  if (query.sort !== 'newest') params.set('sort', query.sort)
  return params
}
