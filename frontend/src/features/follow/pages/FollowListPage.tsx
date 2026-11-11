import { useInfiniteQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { ApiError } from '@/lib/apiClient'
import { cn } from '@/lib/utils'
import { fetchFollowList, userKeys } from '../api'
import { FollowButton } from '../components/FollowButton'
import type { FollowListKind } from '../types'

const TITLES: Record<FollowListKind, string> = { following: 'フォロー中', followers: 'フォロワー' }
const EMPTY: Record<FollowListKind, string> = {
  following: 'まだ誰もフォローしていません。',
  followers: 'まだフォロワーはいません。',
}

/** フォロー中一覧・フォロワー一覧（フォローした日時の新しい順。各行にフォローボタン）。 */
export function FollowListPage({ kind }: { kind: FollowListKind }) {
  const { username = '' } = useParams()
  const auth = useAuth()
  const myUsername = auth.status === 'authenticated' ? auth.user.username.toLowerCase() : null

  const query = useInfiniteQuery({
    queryKey: userKeys.list(username, kind),
    queryFn: ({ pageParam }) => fetchFollowList(username, kind, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    // 各行の followedByMe はログイン状態で変わるため、確認が終わってから取得する
    enabled: auth.status !== 'loading',
  })

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <Link to={`/users/${username}`} className="text-sm text-muted-foreground hover:underline">
          ← @{username} のプロフィール
        </Link>
        <h1 className="text-2xl font-bold">
          @{username} の{TITLES[kind]}
        </h1>
        <nav aria-label="フォロー一覧の切り替え" className="flex gap-4 text-sm">
          {(['following', 'followers'] as const).map((k) => (
            <Link
              key={k}
              to={`/users/${username}/${k}`}
              aria-current={k === kind ? 'page' : undefined}
              className={cn(
                'border-b-2 pb-1',
                k === kind ? 'border-primary font-semibold' : 'border-transparent text-muted-foreground',
              )}
            >
              {TITLES[k]}
            </Link>
          ))}
        </nav>
      </div>

      {query.isError ? (
        query.error instanceof ApiError && query.error.status === 404 ? (
          <p className="text-muted-foreground">@{username} というユーザーは存在しません。</p>
        ) : (
          <div className="space-y-2">
            <p className="text-sm text-destructive">一覧を読み込めませんでした。</p>
            <Button variant="outline" size="sm" onClick={() => query.refetch()}>
              再読み込み
            </Button>
          </div>
        )
      ) : !query.data ? (
        <p role="status" className="text-sm text-muted-foreground">
          読み込み中…
        </p>
      ) : query.data.pages[0].items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{EMPTY[kind]}</p>
      ) : (
        <div className="space-y-6">
          <ul aria-label={`@${username} の${TITLES[kind]}`} className="divide-y rounded-lg border">
            {query.data.pages
              .flatMap((page) => page.items)
              .map((user) => (
                <li key={user.id} className="flex items-center gap-3 p-3">
                  <Link to={`/users/${user.username}`} className="flex min-w-0 flex-1 items-center gap-3">
                    <UserAvatar displayName={user.displayName} avatarUrl={user.avatarUrl} />
                    <span className="min-w-0">
                      <span className="block truncate font-medium hover:underline">{user.displayName}</span>
                      <span className="block truncate text-sm text-muted-foreground">@{user.username}</span>
                    </span>
                  </Link>
                  {user.username.toLowerCase() !== myUsername && (
                    <FollowButton username={user.username} following={user.followedByMe} />
                  )}
                </li>
              ))}
          </ul>
          {query.hasNextPage && (
            <div className="text-center">
              <Button
                variant="outline"
                onClick={() => query.fetchNextPage()}
                disabled={query.isFetchingNextPage}
              >
                {query.isFetchingNextPage ? '読み込み中…' : 'もっと見る'}
              </Button>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
