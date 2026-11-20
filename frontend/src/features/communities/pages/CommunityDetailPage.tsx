import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Crown, MessagesSquare, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/useAuth'
import type { BoardTopicSummary } from '@/features/board/api'
import { AuthorLink } from '@/features/board/components/AuthorLink'
import { TopicForm } from '@/features/board/components/TopicForm'
import { useSpotIdParam } from '@/features/spots/useSpotIdParam'
import { ApiError } from '@/lib/apiClient'
import { formatDateTime } from '@/lib/date'
import {
  communityKeys,
  fetchCommunity,
  fetchCommunityTopics,
  fetchMembers,
  useCreateCommunityTopic,
  useDeleteCommunity,
  type Community,
} from '../api'
import { CategoryBadges } from '../components/CategoryBadges'
import { JoinButton } from '../components/JoinButton'

/**
 * コミュニティの詳細画面（docs/features/12_communities.md 3章）。
 * 説明・オーナー・メンバー数・参加ボタン、メンバー同士のトピック（内輪トーク）、メンバー一覧を表示する。
 */
export function CommunityDetailPage() {
  // URL の :id を正の整数として読む（スポットと同じ規則）
  const communityId = useSpotIdParam()
  if (communityId === null) return <CommunityNotFound />
  return <CommunityDetail communityId={communityId} />
}

function CommunityNotFound() {
  return (
    <section className="space-y-4 text-center">
      <h1 className="text-2xl font-bold">コミュニティが見つかりません</h1>
      <p className="text-muted-foreground">削除された可能性があります。</p>
      <Link to="/communities" className={buttonVariants()}>
        コミュニティ一覧へ戻る
      </Link>
    </section>
  )
}

function CommunityDetail({ communityId }: { communityId: number }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const {
    data: community,
    isPending,
    error,
  } = useQuery({
    queryKey: communityKeys.detail(communityId),
    queryFn: () => fetchCommunity(communityId),
    // 参加状態（joined）はログイン中のユーザーで変わるため、ログイン状態が分かってから読む
    enabled: auth.status !== 'loading',
  })

  if (isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (error) {
    return error instanceof ApiError && error.status === 404 ? (
      <CommunityNotFound />
    ) : (
      <p className="text-sm text-destructive">コミュニティを読み込めませんでした。</p>
    )
  }

  const isOwner = auth.status === 'authenticated' && auth.user.id === community.owner.id

  return (
    <article className="space-y-8">
      <Link to="/communities" className="text-sm text-muted-foreground hover:underline">
        ← コミュニティ一覧
      </Link>
      <header className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-2">
            <h1 className="text-2xl font-bold">{community.name}</h1>
            <CategoryBadges categories={community.categories} />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {community.joined && !isOwner && <Badge>参加中</Badge>}
            <JoinButton community={community} />
            {isOwner && (
              <DeleteCommunityButton
                community={community}
                onDeleted={() => navigate('/communities', { replace: true })}
              />
            )}
          </div>
        </div>
        <p className="leading-relaxed whitespace-pre-wrap">{community.description}</p>
        <dl className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-muted-foreground">
          <div className="inline-flex items-center gap-1.5">
            <dt className="inline-flex items-center gap-1">
              <Crown aria-hidden className="size-4" />
              オーナー
            </dt>
            <dd>
              <AuthorLink author={community.owner} />
            </dd>
          </div>
          <div className="inline-flex items-center gap-1">
            <dt className="inline-flex items-center gap-1">
              <Users aria-hidden className="size-4" />
              メンバー
            </dt>
            <dd className="tabular-nums">{community.memberCount}人</dd>
          </div>
          <div className="inline-flex items-center gap-1">
            <dt className="inline-flex items-center gap-1">
              <MessagesSquare aria-hidden className="size-4" />
              トピック
            </dt>
            <dd className="tabular-nums">{community.topicCount}件</dd>
          </div>
          <div className="inline-flex items-center gap-1">
            <dt>作成</dt>
            <dd>
              <time dateTime={community.createdAt}>{formatDateTime(community.createdAt)}</time>
            </dd>
          </div>
        </dl>
      </header>

      <div className="grid gap-8 lg:grid-cols-[1fr_16rem]">
        <Topics community={community} />
        <Members community={community} />
      </div>
    </article>
  )
}

function DeleteCommunityButton({ community, onDeleted }: { community: Community; onDeleted: () => void }) {
  const [open, setOpen] = useState(false)
  const deleteCommunity = useDeleteCommunity(community.id)

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={<Button variant="destructive" />}>コミュニティを削除</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>コミュニティを削除しますか？</AlertDialogTitle>
          <AlertDialogDescription>
            「{community.name}
            」を削除します。メンバーの参加情報とトピック・返信もすべて削除され、元に戻せません。
          </AlertDialogDescription>
        </AlertDialogHeader>
        {deleteCommunity.isError && (
          <p role="alert" className="text-sm text-destructive">
            削除できませんでした。時間をおいて再度お試しください。
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel>キャンセル</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={() =>
              deleteCommunity.mutate(undefined, {
                onSuccess: () => {
                  setOpen(false)
                  onDeleted()
                },
              })
            }
            disabled={deleteCommunity.isPending}
          >
            {deleteCommunity.isPending ? '削除中…' : '削除する'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

/** メンバー同士のトピック（最後に書き込みがあった順）と、メンバー向けの作成フォーム。 */
function Topics({ community }: { community: Community }) {
  const navigate = useNavigate()
  const createTopic = useCreateCommunityTopic(community.id)
  const query = useInfiniteQuery({
    queryKey: communityKeys.topics(community.id),
    queryFn: ({ pageParam }) => fetchCommunityTopics(community.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })
  // 返信でトピックの順番が動くと、次のページに同じトピックが再び返り得るため、先に出たものだけを表示する
  const seen = new Set<number>()
  const topics = (query.data?.pages ?? [])
    .flatMap((page) => page.items)
    .filter((topic) => !seen.has(topic.id) && seen.add(topic.id))

  return (
    <section aria-labelledby="community-topics-heading" className="min-w-0 space-y-4">
      <h2 id="community-topics-heading" className="text-xl font-semibold">
        メンバーのトピック
      </h2>
      {community.joined ? (
        <Card>
          <CardHeader>
            <CardTitle>
              <h3>新しいトピックを作成</h3>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <TopicForm
              onSubmit={async (input) => {
                const topic = await createTopic.mutateAsync(input)
                navigate(`/community/topics/${topic.id}`)
              }}
            />
          </CardContent>
        </Card>
      ) : (
        <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
          コミュニティに参加すると、トピックの作成や返信ができます。
        </p>
      )}

      {query.isPending && (
        <p role="status" className="text-sm text-muted-foreground">
          読み込み中…
        </p>
      )}
      {query.isError && <p className="text-sm text-destructive">トピックを読み込めませんでした。</p>}
      {query.isSuccess && topics.length === 0 && (
        <p className="text-sm text-muted-foreground">まだトピックはありません。</p>
      )}
      {topics.length > 0 && (
        <ul aria-label="トピック" className="divide-y rounded-lg border">
          {topics.map((topic) => (
            <li key={topic.id}>
              <TopicRow topic={topic} />
            </li>
          ))}
        </ul>
      )}
      {query.hasNextPage && (
        <div className="text-center">
          <Button variant="outline" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? '読み込み中…' : 'もっと見る'}
          </Button>
        </div>
      )}
    </section>
  )
}

function TopicRow({ topic }: { topic: BoardTopicSummary }) {
  return (
    <article className="space-y-2 p-4">
      <h3 className="font-semibold">
        <Link to={`/community/topics/${topic.id}`} className="hover:underline">
          {topic.title}
        </Link>
      </h3>
      <p className="text-sm whitespace-pre-line text-muted-foreground">{topic.bodyExcerpt}</p>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <AuthorLink author={topic.author} />
        <span className="inline-flex items-center gap-1">
          <MessagesSquare aria-hidden className="size-3.5" />
          返信 {topic.replyCount}件
        </span>
        <span>
          最終更新 <time dateTime={topic.lastActivityAt}>{formatDateTime(topic.lastActivityAt)}</time>
        </span>
      </p>
    </article>
  )
}

/** メンバー一覧（参加した順。オーナーが先頭）。 */
function Members({ community }: { community: Community }) {
  const query = useInfiniteQuery({
    queryKey: communityKeys.members(community.id),
    queryFn: ({ pageParam }) => fetchMembers(community.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })
  const members = query.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section aria-labelledby="members-heading" className="space-y-3">
      <h2 id="members-heading" className="text-xl font-semibold">
        メンバー（{community.memberCount}人）
      </h2>
      {query.isPending && (
        <p role="status" className="text-sm text-muted-foreground">
          読み込み中…
        </p>
      )}
      {query.isError && <p className="text-sm text-destructive">メンバーを読み込めませんでした。</p>}
      {members.length > 0 && (
        <ul aria-label="メンバー" className="space-y-2">
          {members.map((member) => (
            <li key={member.user.id} className="flex flex-wrap items-center gap-2 text-sm">
              <AuthorLink author={member.user} />
              {member.owner && <Badge variant="outline">オーナー</Badge>}
            </li>
          ))}
        </ul>
      )}
      {query.hasNextPage && (
        <Button
          variant="outline"
          size="sm"
          onClick={() => query.fetchNextPage()}
          disabled={query.isFetchingNextPage}
        >
          {query.isFetchingNextPage ? '読み込み中…' : 'さらに表示'}
        </Button>
      )}
    </section>
  )
}
