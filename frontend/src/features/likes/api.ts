import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { PAGE_SIZE, spotKeys } from '@/features/spots/api'
import type { Spot, SpotPage } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'

/** PUT / DELETE /api/v1/spots/{id}/like のレスポンス。 */
export type LikeResult = { spotId: number; liked: boolean; likeCount: number }

/** 行ってみたい！を付ける（liked = true）・外す（false）。どちらも冪等。 */
export function setLike(spotId: number, liked: boolean): Promise<LikeResult> {
  return apiFetch<LikeResult>(`/api/v1/spots/${spotId}/like`, { method: liked ? 'PUT' : 'DELETE' })
}

/** 自分が行ってみたい！した投稿（GET /api/v1/me/likes）。 */
export function fetchMyLikes(cursor: string | null): Promise<SpotPage> {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return apiFetch<SpotPage>(`/api/v1/me/likes?${params}`)
}

function isSpotPages(data: unknown): data is InfiniteData<SpotPage> {
  return typeof data === 'object' && data !== null && 'pages' in data
}

function isSpot(data: unknown): data is Spot {
  return typeof data === 'object' && data !== null && 'id' in data && 'likeCount' in data
}

/**
 * 取得済みの詳細・一覧に、行ってみたい！の結果を反映する（画面を移動しても表示がずれないようにする）。
 * 一覧を取り直すと位置が変わるため、件数だけを書き換える。
 */
export function applyLikeResult(queryClient: QueryClient, result: LikeResult): void {
  const patch = <T extends { id: number; likeCount: number; likedByMe: boolean }>(item: T): T =>
    item.id === result.spotId ? { ...item, likeCount: result.likeCount, likedByMe: result.liked } : item

  queryClient.setQueriesData({ queryKey: spotKeys.all }, (data: unknown) => {
    if (isSpotPages(data)) {
      return { ...data, pages: data.pages.map((page) => ({ ...page, items: page.items.map(patch) })) }
    }
    if (isSpot(data)) return patch(data)
    return data
  })
  // 「行ってみたい！した投稿」は次に開いたときに取り直す（今開いている一覧からは消さない）
  void queryClient.invalidateQueries({ queryKey: spotKeys.myLikes(), refetchType: 'none' })
}
