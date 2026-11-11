import { useInfiniteQuery, type QueryKey } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import type { SpotPage } from '../types'
import { SpotCard } from './SpotCard'

type SpotListProps = {
  queryKey: QueryKey
  fetchPage: (cursor: string | null) => Promise<SpotPage>
  /** 0件のときの表示（文字列なら控えめな文章として表示する） */
  emptyMessage: ReactNode
  /** 一覧のラベル（スクリーンリーダー向け） */
  label: string
  /** false の間は取得しない（ログイン状態の確認中など） */
  enabled?: boolean
  /** エラー時の表示を差し替える（null を返すと既定の表示） */
  renderError?: (error: Error) => ReactNode | null
}

/** スポット一覧（カーソルページング、「もっと見る」で続きを読み込む）。 */
export function SpotList({
  queryKey,
  fetchPage,
  emptyMessage,
  label,
  enabled = true,
  renderError,
}: SpotListProps) {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchPage(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    enabled,
  })

  if (query.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (query.isError) {
    const custom = renderError?.(query.error)
    if (custom) return custom
    return (
      <div className="space-y-2">
        <p className="text-sm text-destructive">スポットを読み込めませんでした。</p>
        <Button variant="outline" size="sm" onClick={() => query.refetch()}>
          再読み込み
        </Button>
      </div>
    )
  }

  const spots = query.data.pages.flatMap((page) => page.items)
  if (spots.length === 0) {
    return typeof emptyMessage === 'string' ? (
      <p className="text-sm text-muted-foreground">{emptyMessage}</p>
    ) : (
      emptyMessage
    )
  }

  return (
    <div className="space-y-6">
      <ul aria-label={label} className="grid gap-4 sm:grid-cols-2">
        {spots.map((spot) => (
          <li key={spot.id}>
            <SpotCard spot={spot} />
          </li>
        ))}
      </ul>
      {query.hasNextPage && (
        <div className="text-center">
          <Button variant="outline" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? '読み込み中…' : 'もっと見る'}
          </Button>
        </div>
      )}
    </div>
  )
}
