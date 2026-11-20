import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ThumbsUp } from 'lucide-react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { TapButton } from '@/components/TapButton'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/utils'
import { applyHelpfulResult, setHelpful } from '../api'

type HelpfulButtonProps = {
  spotId: number
  helpful: boolean
  helpfulCount: number
  size?: 'sm' | 'default'
}

type HelpfulState = { helpful: boolean; helpfulCount: number }

/**
 * 「参考になった」ボタン（docs/features/11_community_reactions.md 3章）。
 * 行ってみたい！と同じく、押した瞬間に切り替え（楽観的更新）、失敗したら元に戻してトーストで知らせる。
 */
export function HelpfulButton({ spotId, helpful, helpfulCount, size = 'sm' }: HelpfulButtonProps) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [optimistic, setOptimistic] = useState<HelpfulState | null>(null)

  const mutation = useMutation({
    mutationFn: (next: boolean) => setHelpful(spotId, next),
    onSuccess: (result) => applyHelpfulResult(queryClient, result),
    onError: () => toast.error('参考になったを更新できませんでした。時間をおいて再度お試しください'),
    onSettled: () => setOptimistic(null),
  })

  const shown = optimistic ?? { helpful, helpfulCount }

  const toggle = () => {
    if (auth.status === 'loading') return
    if (auth.status === 'anonymous') {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    const next = !shown.helpful
    setOptimistic({ helpful: next, helpfulCount: Math.max(0, shown.helpfulCount + (next ? 1 : -1)) })
    mutation.mutate(next)
  }

  return (
    <TapButton
      type="button"
      variant={shown.helpful ? 'secondary' : 'outline'}
      size={size}
      aria-pressed={shown.helpful}
      disabled={mutation.isPending}
      onClick={toggle}
      className="disabled:opacity-100"
    >
      <ThumbsUp aria-hidden className={cn(shown.helpful && 'fill-sky-500 text-sky-600')} />
      参考になった
      <span className="tabular-nums">{shown.helpfulCount}</span>
    </TapButton>
  )
}
