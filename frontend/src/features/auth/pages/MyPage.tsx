import { useQuery } from '@tanstack/react-query'
import { useId, type KeyboardEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { fetchMyLikes } from '@/features/likes/api'
import { spotKeys } from '@/features/spots/api'
import { SpotList } from '@/features/spots/components/SpotList'
import { fetchStatusSummary, statusKeys } from '@/features/status/api'
import { MyStatusList } from '@/features/status/components/MyStatusList'
import { cn } from '@/lib/utils'
import { fetchMe } from '../api'

/** マイページ（ログイン必須）。アカウント情報と、マイリスト（行きたい / 訪問済み / 行ってみたい！）のタブ。 */
export function MyPage() {
  const { data: me, isPending, isError } = useQuery({ queryKey: ['users', 'me'], queryFn: fetchMe })

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">マイページ</h1>
      <Card>
        <CardHeader>
          <CardTitle>アカウント情報</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {isPending && <p className="text-sm text-muted-foreground">読み込み中…</p>}
          {isError && <p className="text-sm text-destructive">アカウント情報を取得できませんでした。</p>}
          {me && (
            <>
              <div className="flex items-center gap-4">
                <UserAvatar displayName={me.displayName} avatarUrl={me.avatarUrl} size="lg" />
                <div className="min-w-0">
                  <p className="text-lg font-semibold">{me.displayName}</p>
                  <p className="text-sm text-muted-foreground">@{me.username}</p>
                </div>
              </div>
              {me.bio && <p className="text-sm whitespace-pre-wrap">{me.bio}</p>}
              <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
                <dt className="text-muted-foreground">メールアドレス</dt>
                <dd>{me.email}</dd>
              </dl>
              <div className="flex flex-wrap gap-2">
                <Link to="/settings/profile" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                  プロフィールを編集
                </Link>
                <Link to="/settings/password" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
                  パスワードを変更
                </Link>
                <Link
                  to={`/users/${me.username}`}
                  className={buttonVariants({ variant: 'ghost', size: 'sm' })}
                >
                  自分の投稿
                </Link>
              </div>
            </>
          )}
        </CardContent>
      </Card>
      <MyListTabs />
    </section>
  )
}

type TabKey = 'want_to_go' | 'visited' | 'likes'
const TAB_KEYS: TabKey[] = ['want_to_go', 'visited', 'likes']
const TAB_LABELS: Record<TabKey, string> = {
  want_to_go: '行きたい',
  visited: '訪問済み',
  likes: '行ってみたい！した投稿',
}

/** URL の ?tab= を読む（不正な値は「行きたい」）。 */
function parseTab(value: string | null): TabKey {
  return TAB_KEYS.includes(value as TabKey) ? (value as TabKey) : 'want_to_go'
}

/**
 * マイリストのタブ（行きたい / 訪問済み / 行ってみたい！した投稿）。選択中のタブは URL（?tab=）に持つ。
 * リストは本人だけが見られる（docs/features/08_spot_status.md）。
 */
function MyListTabs() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = parseTab(searchParams.get('tab'))
  const summary = useQuery({ queryKey: statusKeys.summary(), queryFn: fetchStatusSummary })
  const counts: Partial<Record<TabKey, number>> = summary.data
    ? { want_to_go: summary.data.wantToGo, visited: summary.data.visited }
    : {}
  const panelId = useId()

  const select = (next: TabKey) =>
    setSearchParams(next === 'want_to_go' ? {} : { tab: next }, { replace: true })

  // 左右キーでタブを移動する（WAI-ARIA の Tabs パターン）
  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    const delta = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0
    if (!delta) return
    e.preventDefault()
    const next = TAB_KEYS[(TAB_KEYS.indexOf(tab) + delta + TAB_KEYS.length) % TAB_KEYS.length]
    select(next)
    document.getElementById(`${panelId}-tab-${next}`)?.focus()
  }

  return (
    <section className="space-y-4">
      <h2 className="text-xl font-bold">マイリスト</h2>
      <div role="tablist" aria-label="マイリスト" className="flex gap-1 overflow-x-auto border-b">
        {TAB_KEYS.map((key) => (
          <button
            key={key}
            id={`${panelId}-tab-${key}`}
            type="button"
            role="tab"
            aria-selected={key === tab}
            aria-controls={panelId}
            tabIndex={key === tab ? 0 : -1}
            onClick={() => select(key)}
            onKeyDown={onKeyDown}
            className={cn(
              '-mb-px shrink-0 border-b-2 px-3 py-2 text-sm whitespace-nowrap',
              key === tab
                ? 'border-primary font-semibold'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {TAB_LABELS[key]}
            {counts[key] !== undefined && <span className="ml-1 tabular-nums">（{counts[key]}）</span>}
          </button>
        ))}
      </div>
      <div id={panelId} role="tabpanel" aria-labelledby={`${panelId}-tab-${tab}`}>
        {tab === 'likes' ? (
          <SpotList
            queryKey={spotKeys.myLikes()}
            fetchPage={fetchMyLikes}
            emptyMessage="まだ行ってみたい！した投稿はありません。気になるスポットのハートを押してみましょう。"
            label="行ってみたい！した投稿"
          />
        ) : (
          <MyStatusList
            key={tab}
            status={tab === 'visited' ? 'VISITED' : 'WANT_TO_GO'}
            label={`${TAB_LABELS[tab]}のスポット`}
          />
        )}
      </div>
    </section>
  )
}
