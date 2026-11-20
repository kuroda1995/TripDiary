import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Bookmark } from 'lucide-react'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import type { SpotStatus } from '@/features/spots/types'
import { cn } from '@/lib/utils'
import { applyStatusChange, setSpotStatus } from '../api'

/**
 * ワンタップの「行きたい」ボタン（ブックマーク。docs/features/11_community_reactions.md 3章）。
 *
 * マイリストの「行きたい」（WANT_TO_GO）を付け外しする。保存したスポットはマイページの「行きたい」タブで確認できる。
 * すでに「訪問済み」のスポットでは押せない（訪問済みを消さないため。変更はマイリストの選択から行う）。
 */
export function WantToGoButton({ spotId, status }: { spotId: number; status: SpotStatus | null }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [optimistic, setOptimistic] = useState<SpotStatus | null | undefined>(undefined)

  const mutation = useMutation({
    mutationFn: (next: SpotStatus | null) => setSpotStatus(spotId, next),
    onSuccess: (_, next) => applyStatusChange(queryClient, spotId, next),
    onError: () => toast.error('行きたいリストを更新できませんでした。時間をおいて再度お試しください'),
    onSettled: () => setOptimistic(undefined),
  })

  const shown = optimistic === undefined ? status : optimistic
  if (shown === 'VISITED') return null
  const saved = shown === 'WANT_TO_GO'

  const toggle = () => {
    if (auth.status === 'loading') return
    if (auth.status === 'anonymous') {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    const next = saved ? null : 'WANT_TO_GO'
    setOptimistic(next)
    mutation.mutate(next)
  }

  return (
    <Button
      type="button"
      variant={saved ? 'secondary' : 'outline'}
      aria-pressed={saved}
      disabled={mutation.isPending}
      onClick={toggle}
      className="disabled:opacity-100"
    >
      <Bookmark aria-hidden className={cn(saved && 'fill-amber-400 text-amber-500')} />
      行きたい
    </Button>
  )
}
