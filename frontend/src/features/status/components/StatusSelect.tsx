import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Bookmark } from 'lucide-react'
import { useId, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import type { SpotStatus } from '@/features/spots/types'
import { cn } from '@/lib/utils'
import { applyStatusChange, setSpotStatus } from '../api'

const STATUS_LABELS: Record<SpotStatus, string> = { WANT_TO_GO: '行きたい', VISITED: '訪問済み' }

const NONE = ''

type StatusSelectProps = {
  spotId: number
  status: SpotStatus | null
  /** 画面上のラベル（カードでは省略して読み上げだけにする） */
  showLabel?: boolean
}

/**
 * ステータスの選択（未設定 / 行きたい / 訪問済み。docs/features/08_spot_status.md 3章）。
 *
 * - キーボード・スマホでも扱いやすいよう、ブラウザ標準の select を使う
 * - 選んだ瞬間に表示を切り替え（楽観的更新）、失敗したら元に戻してトーストで知らせる
 * - 未ログインなら select の代わりにボタンを出し、押すとログイン画面へ（ログイン後にこの画面へ戻る）
 */
export function StatusSelect({ spotId, status, showLabel = false }: StatusSelectProps) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const selectId = useId()
  // 通信中だけ表示を上書きする（undefined = 上書きなし）
  const [optimistic, setOptimistic] = useState<SpotStatus | null | undefined>(undefined)

  const mutation = useMutation({
    mutationFn: (next: SpotStatus | null) => setSpotStatus(spotId, next),
    onSuccess: (_, next) => applyStatusChange(queryClient, spotId, next),
    onError: () => toast.error('マイリストを更新できませんでした。時間をおいて再度お試しください'),
    onSettled: () => setOptimistic(undefined),
  })

  if (auth.status !== 'authenticated') {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={auth.status === 'loading'}
        onClick={() => navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)}
      >
        <Bookmark aria-hidden />
        マイリストに追加
      </Button>
    )
  }

  const shown = optimistic === undefined ? status : optimistic

  return (
    <div className="inline-flex items-center gap-2 text-sm">
      <label htmlFor={selectId} className={cn(!showLabel && 'sr-only')}>
        マイリスト
      </label>
      <span className="relative inline-flex items-center">
        <Bookmark
          aria-hidden
          className={cn(
            'pointer-events-none absolute left-2 size-4',
            shown ? 'fill-amber-400 text-amber-500' : 'text-muted-foreground',
          )}
        />
        <select
          id={selectId}
          value={shown ?? NONE}
          disabled={mutation.isPending}
          onChange={(e) => {
            const next = e.target.value === NONE ? null : (e.target.value as SpotStatus)
            setOptimistic(next)
            mutation.mutate(next)
          }}
          // 通信中も選んだ直後の見た目のまま（無効化で薄くならないようにする）
          className="h-7 rounded-md border bg-background pr-2 pl-7 text-xs disabled:opacity-100"
        >
          <option value={NONE}>マイリスト未登録</option>
          <option value="WANT_TO_GO">{STATUS_LABELS.WANT_TO_GO}</option>
          <option value="VISITED">{STATUS_LABELS.VISITED}</option>
        </select>
      </span>
    </div>
  )
}
