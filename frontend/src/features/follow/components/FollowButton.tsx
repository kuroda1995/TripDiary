import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/utils'
import { applyFollowResult, setFollow } from '../api'

type FollowButtonProps = {
  username: string
  following: boolean
  size?: 'sm' | 'default'
}

/**
 * フォローボタン（docs/features/06_follow.md 3章）。自分自身には表示しない前提（呼び出し側で判定する）。
 *
 * - 「フォローする」⇔「フォロー中」を押した瞬間に切り替え（楽観的更新）、失敗したら元に戻してトーストで知らせる
 * - 「フォロー中」にカーソルを乗せる（キーボードで選ぶ）と「フォロー解除」と表示する
 * - 未ログインならログイン画面へ（ログイン後にこの画面へ戻る）
 */
export function FollowButton({ username, following, size = 'sm' }: FollowButtonProps) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  // 通信中だけ表示を上書きする。終わったら（成功時はキャッシュを更新済みの）props の値に戻す
  const [optimistic, setOptimistic] = useState<boolean | null>(null)

  const mutation = useMutation({
    mutationFn: (next: boolean) => setFollow(username, next),
    onSuccess: (result) => {
      if (auth.status === 'authenticated') applyFollowResult(queryClient, result, auth.user.username)
    },
    onError: () => toast.error('フォローを更新できませんでした。時間をおいて再度お試しください'),
    onSettled: () => setOptimistic(null),
  })

  const shown = optimistic ?? following

  const toggle = () => {
    if (auth.status === 'loading') return
    if (auth.status === 'anonymous') {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    setOptimistic(!shown)
    mutation.mutate(!shown)
  }

  return (
    <Button
      type="button"
      variant={shown ? 'outline' : 'default'}
      size={size}
      // 表示が「フォロー中 / フォロー解除」と切り替わっても、読み上げは押すと何が起きるかで統一する
      // （ラベル自体が状態で変わるため aria-pressed は付けない）
      aria-label={shown ? `@${username} のフォローを解除` : `@${username} をフォローする`}
      disabled={mutation.isPending}
      onClick={toggle}
      // 通信中も押した直後の見た目のまま（無効化で薄くならないようにする）
      className={cn(
        'group min-w-28 disabled:opacity-100',
        shown && 'hover:border-destructive hover:bg-destructive/10 hover:text-destructive',
      )}
    >
      {shown ? (
        <>
          <span className="group-hover:hidden group-focus-visible:hidden">フォロー中</span>
          <span className="hidden group-hover:inline group-focus-visible:inline">フォロー解除</span>
        </>
      ) : (
        'フォローする'
      )}
    </Button>
  )
}
