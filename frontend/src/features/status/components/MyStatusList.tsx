import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { MapPinCheck } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { SpotCard } from '@/features/spots/components/SpotCard'
import type { SpotStatus } from '@/features/spots/types'
import { formatDate, todayInJapan } from '@/lib/date'
import { applyStatusChange, fetchMyList, setSpotStatus, statusKeys } from '../api'

const EMPTY: Record<SpotStatus, string> = {
  WANT_TO_GO:
    'まだ「行きたい」スポットはありません。気になるスポットのマイリストから「行きたい」を選んでみましょう。',
  VISITED: 'まだ「訪問済み」のスポットはありません。',
}

/** マイリスト（ステータスを更新した日時の新しい順）。「行きたい」には「行った！」ボタンを付ける。 */
export function MyStatusList({ status, label }: { status: SpotStatus; label: string }) {
  const query = useInfiniteQuery({
    queryKey: statusKeys.list(status),
    queryFn: ({ pageParam }) => fetchMyList(status, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })

  if (query.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (query.isError) {
    return (
      <div className="space-y-2">
        <p className="text-sm text-destructive">マイリストを読み込めませんでした。</p>
        <Button variant="outline" size="sm" onClick={() => query.refetch()}>
          再読み込み
        </Button>
      </div>
    )
  }

  const items = query.data.pages.flatMap((page) => page.items)
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">{EMPTY[status]}</p>
  }

  return (
    <div className="space-y-6">
      <ul aria-label={label} className="grid gap-4 sm:grid-cols-2">
        {items.map((item) => (
          <li key={item.spot.id} className="space-y-2">
            <SpotCard spot={item.spot} />
            <div className="flex items-center justify-between gap-2 px-1 text-xs text-muted-foreground">
              <span>
                {item.visitedAt
                  ? `${formatInstantDate(item.visitedAt)}に訪問済みにしました`
                  : `${formatInstantDate(item.updatedAt)}に追加`}
              </span>
              {status === 'WANT_TO_GO' && <VisitedButton spotId={item.spot.id} title={item.spot.title} />}
            </div>
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

/** 日時（UTC）→ 日本時間の日付 "2026年11月11日" */
function formatInstantDate(isoDateTime: string): string {
  return formatDate(todayInJapan(new Date(isoDateTime)))
}

/** 「行きたい」から「訪問済み」にワンタップで変える。 */
function VisitedButton({ spotId, title }: { spotId: number; title: string }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => setSpotStatus(spotId, 'VISITED'),
    onSuccess: () => {
      applyStatusChange(queryClient, spotId, 'VISITED')
      toast.success(`「${title}」を訪問済みにしました`)
    },
    onError: () => toast.error('マイリストを更新できませんでした。時間をおいて再度お試しください'),
  })

  return (
    <Button
      type="button"
      size="sm"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
      aria-label={`「${title}」に行った！（訪問済みにする）`}
    >
      <MapPinCheck aria-hidden />
      行った！
    </Button>
  )
}
