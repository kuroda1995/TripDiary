import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Heart } from 'lucide-react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/utils'
import { applyLikeResult, setLike } from '../api'

type LikeButtonProps = {
  spotId: number
  liked: boolean
  likeCount: number
  size?: 'sm' | 'default'
}

type LikeState = { liked: boolean; likeCount: number }

/**
 * 「行ってみたい！」ボタン（docs/features/04_like.md 3章）。
 *
 * - 押した瞬間に表示を切り替え（楽観的更新）、API が失敗したら元に戻してトーストで知らせる
 * - 未ログインならログイン画面へ（ログイン後にこの画面へ戻る）
 */
export function LikeButton({ spotId, liked, likeCount, size = 'sm' }: LikeButtonProps) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  // 通信中だけ表示を上書きする。終わったら（成功時はキャッシュを更新済みの）props の値に戻す
  const [optimistic, setOptimistic] = useState<LikeState | null>(null)

  const mutation = useMutation({
    mutationFn: (next: boolean) => setLike(spotId, next),
    onSuccess: (result) => applyLikeResult(queryClient, result),
    onError: () => toast.error('行ってみたい！を更新できませんでした。時間をおいて再度お試しください'),
    onSettled: () => setOptimistic(null),
  })

  const shown = optimistic ?? { liked, likeCount }

  const toggle = () => {
    if (auth.status === 'loading') return
    if (auth.status === 'anonymous') {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    const next = !shown.liked
    setOptimistic({ liked: next, likeCount: Math.max(0, shown.likeCount + (next ? 1 : -1)) })
    mutation.mutate(next)
  }

  return (
    <Button
      type="button"
      variant={shown.liked ? 'secondary' : 'outline'}
      size={size}
      aria-pressed={shown.liked}
      disabled={mutation.isPending}
      onClick={toggle}
      // 通信中も押した直後の見た目のまま（無効化で薄くならないようにする）
      className="disabled:opacity-100"
    >
      <Heart aria-hidden className={cn(shown.liked && 'fill-rose-500 text-rose-500')} />
      行ってみたい！
      <span className="tabular-nums">{shown.likeCount}</span>
    </Button>
  )
}
