import { PAGE_SIZE } from '@/features/spots/api'
import type { SpotPage } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'
import type { SpotSort } from './searchParams'

/** API に渡す検索条件（エリアはスラッグから ID に変換済み）。 */
export type SpotSearchQuery = { areaIds: number[]; q: string; sort: SpotSort }

/** スポット検索（GET /api/v1/spots。条件を省略すると新着一覧と同じ）。 */
export function fetchSpotSearch(query: SpotSearchQuery, cursor: string | null): Promise<SpotPage> {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE), sort: query.sort })
  if (query.areaIds.length > 0) params.set('areaIds', query.areaIds.join(','))
  const q = query.q.trim()
  if (q) params.set('q', q)
  if (cursor) params.set('cursor', cursor)
  return apiFetch<SpotPage>(`/api/v1/spots?${params}`)
}
