import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { PAGE_SIZE, spotKeys } from '@/features/spots/api'
import type { Spot, SpotStatus, SpotSummary } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'

/** PUT /api/v1/spots/{id}/status のレスポンス。 */
export type StatusResult = {
  spotId: number
  status: SpotStatus
  visitedAt: string | null
  updatedAt: string
}

/** マイリストの1件。 */
export type MyListItem = {
  status: SpotStatus
  visitedAt: string | null
  updatedAt: string
  spot: SpotSummary
}

export type MyListPage = { items: MyListItem[]; nextCursor: string | null }

export type StatusSummary = { visited: number; wantToGo: number }

export const statusKeys = {
  all: ['my-statuses'] as const,
  list: (status: SpotStatus) => [...statusKeys.all, 'list', status] as const,
  summary: () => [...statusKeys.all, 'summary'] as const,
}

/** ステータスを設定する（null なら解除）。どちらも冪等。 */
export async function setSpotStatus(spotId: number, status: SpotStatus | null): Promise<void> {
  const path = `/api/v1/spots/${spotId}/status`
  if (status === null) {
    await apiFetch<void>(path, { method: 'DELETE' })
    return
  }
  await apiFetch<StatusResult>(path, { method: 'PUT', body: JSON.stringify({ status }) })
}

export function fetchMyList(status: SpotStatus, cursor: string | null): Promise<MyListPage> {
  const params = new URLSearchParams({ status, limit: String(PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return apiFetch<MyListPage>(`/api/v1/me/spot-statuses?${params}`)
}

export function fetchStatusSummary(): Promise<StatusSummary> {
  return apiFetch<StatusSummary>('/api/v1/me/spot-statuses/summary')
}

function isPages(data: unknown): data is InfiniteData<{ items: unknown[] }> {
  return typeof data === 'object' && data !== null && 'pages' in data
}

function isSpot(data: unknown): data is Spot {
  return typeof data === 'object' && data !== null && 'id' in data && 'myStatus' in data
}

/**
 * 取得済みの詳細・一覧に、ステータスの変更を反映する（画面を移動しても表示がずれないようにする）。
 * マイリストと件数は取り直す（表示中のリストからは、ステータスが変わったスポットが外れる）。
 */
export function applyStatusChange(queryClient: QueryClient, spotId: number, status: SpotStatus | null): void {
  const patch = <T extends { id: number; myStatus: SpotStatus | null }>(spot: T): T =>
    spot.id === spotId ? { ...spot, myStatus: status } : spot

  queryClient.setQueriesData({ queryKey: spotKeys.all }, (data: unknown) => {
    if (isPages(data)) {
      return {
        ...data,
        pages: data.pages.map((page) => ({ ...page, items: (page.items as SpotSummary[]).map(patch) })),
      }
    }
    if (isSpot(data)) return patch(data)
    return data
  })
  void queryClient.invalidateQueries({ queryKey: statusKeys.all })
}
