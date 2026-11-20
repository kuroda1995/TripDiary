import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { spotKeys } from '@/features/spots/api'
import type { Spot, SpotPage } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'

/** PUT / DELETE /api/v1/spots/{id}/helpful のレスポンス。 */
export type HelpfulResult = { spotId: number; helpful: boolean; helpfulCount: number }

/** 参考になったを付ける（true）・外す（false）。どちらも冪等。 */
export function setHelpful(spotId: number, helpful: boolean): Promise<HelpfulResult> {
  return apiFetch<HelpfulResult>(`/api/v1/spots/${spotId}/helpful`, { method: helpful ? 'PUT' : 'DELETE' })
}

function isSpotPages(data: unknown): data is InfiniteData<SpotPage> {
  return typeof data === 'object' && data !== null && 'pages' in data
}

function isSpot(data: unknown): data is Spot {
  return typeof data === 'object' && data !== null && 'id' in data && 'helpfulCount' in data
}

/** 取得済みの詳細・一覧に結果を反映する（画面を移動しても表示がずれないようにする）。 */
export function applyHelpfulResult(queryClient: QueryClient, result: HelpfulResult): void {
  const patch = <T extends { id: number; helpfulCount: number; helpfulByMe: boolean }>(item: T): T =>
    item.id === result.spotId
      ? { ...item, helpfulCount: result.helpfulCount, helpfulByMe: result.helpful }
      : item

  queryClient.setQueriesData({ queryKey: spotKeys.all }, (data: unknown) => {
    if (isSpotPages(data)) {
      return { ...data, pages: data.pages.map((page) => ({ ...page, items: page.items.map(patch) })) }
    }
    if (isSpot(data)) return patch(data)
    return data
  })
}
