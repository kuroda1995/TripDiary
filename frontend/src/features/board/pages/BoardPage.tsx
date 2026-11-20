import { useInfiniteQuery } from '@tanstack/react-query'
import { MessagesSquare } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/useAuth'
import { formatDateTime } from '@/lib/date'
import { boardKeys, fetchTopics, useCreateTopic, type BoardTopicSummary } from '../api'
import { AuthorLink } from '../components/AuthorLink'
import { TopicForm } from '../components/TopicForm'

/**
 * お寺コミュニティ広場（掲示板）のトップ（docs/features/10_community_board.md 3章）。
 * トピックは最後に書き込みがあった順に並ぶ。閲覧は誰でも、作成はログインが必要。
 */
export function BoardPage() {
  const auth = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const createTopic = useCreateTopic()

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-bold">お寺コミュニティ広場</h1>
        <p className="text-sm text-muted-foreground">
          おすすめのお寺の相談や御朱印の情報交換など、自由にトピックを立てて話しましょう。
        </p>
      </header>

      {auth.status === 'authenticated' && (
        <Card>
          <CardHeader>
            <CardTitle>
              <h2>新しいトピックを作成</h2>
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
      )}
      {auth.status === 'anonymous' && (
        <Link
          to={`/login?redirect=${encodeURIComponent(location.pathname)}`}
          className={buttonVariants({ variant: 'outline' })}
        >
          ログインしてトピックを作成する
        </Link>
      )}

      <section aria-labelledby="topics-heading" className="space-y-4">
        <h2 id="topics-heading" className="text-xl font-semibold">
          トピック一覧
        </h2>
        <TopicList />
      </section>
    </section>
  )
}

function TopicList() {
  const query = useInfiniteQuery({
    queryKey: boardKeys.topics(),
    queryFn: ({ pageParam }) => fetchTopics(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })

  if (query.isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (query.isError) {
    return <p className="text-sm text-destructive">トピックを読み込めませんでした。</p>
  }

  // 返信でトピックの順番が動くと、次のページに同じトピックが再び返り得るため、先に出たものだけを表示する
  const seen = new Set<number>()
  const topics = query.data.pages
    .flatMap((page) => page.items)
    .filter((topic) => !seen.has(topic.id) && seen.add(topic.id))
  if (topics.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        まだトピックはありません。最初のトピックを作成してみましょう。
      </p>
    )
  }

  return (
    <div className="space-y-6">
      <ul aria-label="トピック" className="divide-y rounded-lg border">
        {topics.map((topic) => (
          <li key={topic.id}>
            <TopicRow topic={topic} />
          </li>
        ))}
      </ul>
      {query.hasNextPage && (
        <div className="text-center">
          <Button variant="outline" onClick={() => query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {query.isFetchingNextPage ? '読み込み中…' : 'もっと見る'}
          </Button>
        </div>
      )}
    </div>
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
