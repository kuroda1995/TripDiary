import { LogIn, LogOut } from 'lucide-react'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import { Button, buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { useMembership, type Community } from '../api'

/**
 * 「コミュニティに参加する / 退出する」のワンクリック切り替え（docs/features/12_communities.md 3章）。
 * オーナーは退出できないため出さない。未ログインならログインへ案内する。
 */
export function JoinButton({ community }: { community: Community }) {
  const auth = useAuth()
  const location = useLocation()
  const membership = useMembership(community.id)

  if (auth.status === 'loading') return null
  if (auth.status === 'anonymous') {
    return (
      <Link
        to={`/login?redirect=${encodeURIComponent(location.pathname)}`}
        className={buttonVariants({ variant: 'default' })}
      >
        ログインして参加する
      </Link>
    )
  }
  if (auth.user.id === community.owner.id) return null

  const toggle = () =>
    membership.mutate(!community.joined, {
      onSuccess: (result) =>
        toast.success(
          result.joined ? `「${community.name}」に参加しました` : `「${community.name}」から退出しました`,
        ),
      onError: () => toast.error('参加状態を更新できませんでした。時間をおいて再度お試しください'),
    })

  return community.joined ? (
    <Button variant="outline" onClick={toggle} disabled={membership.isPending}>
      <LogOut aria-hidden />
      {membership.isPending ? '更新中…' : '退出する'}
    </Button>
  ) : (
    <Button onClick={toggle} disabled={membership.isPending}>
      <LogIn aria-hidden />
      {membership.isPending ? '更新中…' : 'コミュニティに参加する'}
    </Button>
  )
}
