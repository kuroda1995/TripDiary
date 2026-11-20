import { useInfiniteQuery } from '@tanstack/react-query'
import { Plus, Search, Users } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAuth } from '@/features/auth/useAuth'
import { AuthorLink } from '@/features/board/components/AuthorLink'
import { cn } from '@/lib/utils'
import {
  communityKeys,
  fetchCommunities,
  type CommunitySearchQuery,
  type CommunitySort,
  type CommunitySummary,
} from '../api'
import { COMMUNITY_CATEGORIES, COMMUNITY_CATEGORY_LABELS, type CommunityCategory } from '../categories'
import { CategoryBadges } from '../components/CategoryBadges'
import { MAX_KEYWORD_LENGTH, parseCommunitySearchParams, toCommunitySearchParams } from '../searchParams'

const SORT_LABELS: Record<CommunitySort, string> = { newest: '新着順', popular: 'メンバーが多い順' }

/**
 * コミュニティを探す画面（docs/features/12_communities.md 3章）。
 * 条件は URL のクエリだけに持つ（共有・ブラウザの戻る操作に対応）。カテゴリ・並び順は変えた時点で、キーワードは「検索」で反映する。
 */
export function CommunitiesPage() {
  const auth = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const sortId = useId()
  const query = parseCommunitySearchParams(searchParams)

  const update = (patch: Partial<CommunitySearchQuery>) =>
    setSearchParams(toCommunitySearchParams({ ...query, ...patch }))

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-2">
          <h1 className="text-2xl font-bold">コミュニティ</h1>
          <p className="text-sm text-muted-foreground">
            地域・御朱印・庭園など、テーマごとの仲間が集まる場所です。参加するとメンバー同士でトピックを立てて話せます。
          </p>
        </div>
        {auth.status === 'authenticated' && (
          <Link to="/communities/new" className={buttonVariants()}>
            <Plus aria-hidden />
            コミュニティを作る
          </Link>
        )}
        {auth.status === 'anonymous' && (
          <Link
            to={`/login?redirect=${encodeURIComponent('/communities/new')}`}
            className={buttonVariants({ variant: 'outline' })}
          >
            ログインしてコミュニティを作る
          </Link>
        )}
      </header>

      <div className="space-y-4 rounded-lg border p-4">
        <KeywordForm key={query.q} initial={query.q} onSubmit={(q) => update({ q })} />
        <CategoryFilter value={query.category} onChange={(category) => update({ category })} />
        <div className="flex items-center gap-2 text-sm">
          <label htmlFor={sortId} className="font-medium">
            並び順
          </label>
          <select
            id={sortId}
            value={query.sort}
            onChange={(e) => update({ sort: e.target.value as CommunitySort })}
            className="h-8 rounded-md border bg-background px-2"
          >
            {(Object.keys(SORT_LABELS) as CommunitySort[]).map((sort) => (
              <option key={sort} value={sort}>
                {SORT_LABELS[sort]}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section aria-labelledby="communities-heading" className="space-y-4">
        <h2 id="communities-heading" className="text-xl font-semibold">
          コミュニティ一覧
        </h2>
        <CommunityList query={query} />
      </section>
    </section>
  )
}

function KeywordForm({ initial, onSubmit }: { initial: string; onSubmit: (q: string) => void }) {
  const [value, setValue] = useState(initial)
  const inputId = useId()

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(value.trim())
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="space-y-1.5">
      <label htmlFor={inputId} className="text-sm font-medium">
        キーワード（名前・説明文）
      </label>
      <div className="flex gap-2">
        <Input
          id={inputId}
          type="search"
          value={value}
          maxLength={MAX_KEYWORD_LENGTH}
          placeholder="例: 富山、御朱印、枯山水"
          onChange={(e) => setValue(e.target.value)}
        />
        <Button type="submit">
          <Search aria-hidden />
          検索
        </Button>
      </div>
    </form>
  )
}

/** カテゴリの絞り込み（1つだけ選ぶ。選択中をもう一度押すと解除）。 */
function CategoryFilter({
  value,
  onChange,
}: {
  value: CommunityCategory | null
  onChange: (value: CommunityCategory | null) => void
}) {
  const options: (CommunityCategory | null)[] = [null, ...COMMUNITY_CATEGORIES]
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">カテゴリ</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((category) => {
          const selected = value === category
          return (
            <Button
              key={category ?? 'all'}
              type="button"
              size="sm"
              variant={selected ? 'default' : 'outline'}
              aria-pressed={selected}
              onClick={() => onChange(selected ? null : category)}
            >
              {category ? COMMUNITY_CATEGORY_LABELS[category] : 'すべて'}
            </Button>
          )
        })}
      </div>
    </fieldset>
  )
}

function CommunityList({ query }: { query: CommunitySearchQuery }) {
  const auth = useAuth()
  const result = useInfiniteQuery({
    queryKey: communityKeys.list(query),
    queryFn: ({ pageParam }) => fetchCommunities(query, pageParam),
    // 「参加中」はログイン中のユーザーで変わるため、ログイン状態が分かってから読む
    enabled: auth.status !== 'loading',
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })

  if (result.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (result.isError) {
    return <p className="text-sm text-destructive">コミュニティを読み込めませんでした。</p>
  }

  // 人気順でページをめくる間に人数が変わると、同じコミュニティが再び返り得るため、先に出たものだけを表示する
  const seen = new Set<number>()
  const communities = result.data.pages
    .flatMap((page) => page.items)
    .filter((community) => !seen.has(community.id) && seen.add(community.id))
  if (communities.length === 0) {
    const filtered = query.q !== '' || query.category !== null
    return (
      <p className="text-sm text-muted-foreground">
        {filtered
          ? '該当するコミュニティはありませんでした。条件を変えて検索してください。'
          : 'まだコミュニティはありません。最初のコミュニティを作ってみましょう。'}
      </p>
    )
  }

  return (
    <div className="space-y-6">
      <ul aria-label="コミュニティ" className="grid gap-4 sm:grid-cols-2">
        {communities.map((community) => (
          <li key={community.id}>
            <CommunityCard community={community} />
          </li>
        ))}
      </ul>
      {result.hasNextPage && (
        <div className="text-center">
          <Button
            variant="outline"
            onClick={() => result.fetchNextPage()}
            disabled={result.isFetchingNextPage}
          >
            {result.isFetchingNextPage ? '読み込み中…' : 'もっと見る'}
          </Button>
        </div>
      )}
    </div>
  )
}

function CommunityCard({ community }: { community: CommunitySummary }) {
  return (
    <article
      aria-label={community.name}
      className={cn(
        'flex h-full flex-col gap-3 rounded-lg border p-4',
        community.joined && 'border-primary/50',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <h3 className="font-semibold">
          <Link to={`/communities/${community.id}`} className="hover:underline">
            {community.name}
          </Link>
        </h3>
        {community.joined && <Badge>参加中</Badge>}
      </div>
      <CategoryBadges categories={community.categories} />
      <p className="flex-1 text-sm whitespace-pre-line text-muted-foreground">
        {community.descriptionExcerpt}
      </p>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1">
          <Users aria-hidden className="size-3.5" />
          メンバー {community.memberCount}人
        </span>
        <span className="inline-flex items-center gap-1">
          作成者 <AuthorLink author={community.owner} />
        </span>
      </p>
    </article>
  )
}
