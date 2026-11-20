import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
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
import { Button, buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { CommentForm } from '@/features/comments/components/CommentForm'
import { communityKeys, fetchCommunity } from '@/features/communities/api'
import { useSpotIdParam } from '@/features/spots/useSpotIdParam'
import { ApiError } from '@/lib/apiClient'
import { formatDateTime } from '@/lib/date'
import { boardKeys, fetchReplies, fetchTopic, useDeleteTopic, usePostReply, type BoardTopic } from '../api'
import { AuthorLink } from '../components/AuthorLink'
import { ReplyItem } from '../components/ReplyItem'

export function BoardTopicPage() {
  // URL の :id を正の整数として読む（スポットと同じ規則）
  const topicId = useSpotIdParam()
  if (topicId === null) return <TopicNotFound />
  return <TopicDetail topicId={topicId} />
}

function TopicNotFound() {
  return (
    <section className="space-y-4 text-center">
      <h1 className="text-2xl font-bold">トピックが見つかりません</h1>
      <p className="text-muted-foreground">削除された可能性があります。</p>
      <Link to="/community" className={buttonVariants()}>
        コミュニティ広場へ戻る
      </Link>
    </section>
  )
}

function TopicDetail({ topicId }: { topicId: number }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const {
    data: topic,
    isPending,
    error,
  } = useQuery({
    queryKey: boardKeys.topic(topicId),
    queryFn: () => fetchTopic(topicId),
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
      <TopicNotFound />
    ) : (
      <p className="text-sm text-destructive">トピックを読み込めませんでした。</p>
    )
  }

  const me = auth.status === 'authenticated' ? auth.user : null
  const isOwner = me !== null && me.id === topic.author.id
  // コミュニティ内のトピックは、コミュニティのオーナーも削除できる
  const isCommunityOwner = me !== null && me.id === topic.community?.ownerId
  const backPath = topic.community ? `/communities/${topic.community.id}` : '/community'

  return (
    <article className="space-y-6">
      <Link to={backPath} className="text-sm text-muted-foreground hover:underline">
        ← {topic.community ? topic.community.name : 'コミュニティ広場'}
      </Link>
      <header className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-2xl font-bold">{topic.title}</h1>
          {(isOwner || isCommunityOwner) && (
            <DeleteTopicButton topic={topic} onDeleted={() => navigate(backPath, { replace: true })} />
          )}
        </div>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
          <AuthorLink author={topic.author} />
          <time dateTime={topic.createdAt}>{formatDateTime(topic.createdAt)}</time>
        </p>
      </header>
      <p className="leading-relaxed whitespace-pre-wrap">{topic.body}</p>
      <Replies topic={topic} myId={me?.id ?? null} />
    </article>
  )
}

function DeleteTopicButton({ topic, onDeleted }: { topic: BoardTopic; onDeleted: () => void }) {
  const [open, setOpen] = useState(false)
  const deleteTopic = useDeleteTopic(topic.id, topic.community?.id ?? null)

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={<Button variant="destructive" size="sm" />}>削除</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>トピックを削除しますか？</AlertDialogTitle>
          <AlertDialogDescription>
            「{topic.title}」を削除します。ついた返信も削除され、元に戻せません。
          </AlertDialogDescription>
        </AlertDialogHeader>
        {deleteTopic.isError && (
          <p role="alert" className="text-sm text-destructive">
            削除できませんでした。時間をおいて再度お試しください。
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel>キャンセル</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={() =>
              deleteTopic.mutate(undefined, {
                onSuccess: () => {
                  setOpen(false)
                  onDeleted()
                },
              })
            }
            disabled={deleteTopic.isPending}
          >
            {deleteTopic.isPending ? '削除中…' : '削除する'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

/** 返信（古い順・「さらに表示」で続きを読み込む）と返信フォーム。 */
function Replies({ topic, myId }: { topic: BoardTopic; myId: number | null }) {
  const auth = useAuth()
  const location = useLocation()
  const communityId = topic.community?.id ?? null
  const postReply = usePostReply(topic.id, communityId)
  // コミュニティ内のトピックはメンバーだけが返信できるので、参加状態を読む
  const community = useQuery({
    queryKey: communityKeys.detail(communityId ?? 0),
    queryFn: () => fetchCommunity(communityId!),
    enabled: communityId !== null && myId !== null,
  })
  const canReply = myId !== null && (communityId === null || community.data?.joined === true)
  const query = useInfiniteQuery({
    queryKey: boardKeys.replies(topic.id),
    queryFn: ({ pageParam }) => fetchReplies(topic.id, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })
  const replies = query.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section aria-labelledby="replies-heading" className="space-y-4 border-t pt-6">
      <h2 id="replies-heading" className="text-lg font-bold">
        返信（{topic.replyCount}件）
      </h2>
      {query.isPending && (
        <p role="status" className="text-sm text-muted-foreground">
          読み込み中…
        </p>
      )}
      {query.isError && <p className="text-sm text-destructive">返信を読み込めませんでした。</p>}
      {query.isSuccess && replies.length === 0 && (
        <p className="text-sm text-muted-foreground">まだ返信はありません。</p>
      )}
      {replies.length > 0 && (
        <ol aria-label="返信" className="divide-y">
          {replies.map((reply) => (
            <li key={reply.id}>
              <ReplyItem
                reply={reply}
                isMine={myId !== null && myId === reply.author.id}
                isOnMyTopic={myId !== null && myId === topic.author.id}
                isInMyCommunity={myId !== null && myId === topic.community?.ownerId}
                communityId={communityId}
              />
            </li>
          ))}
        </ol>
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

      {canReply ? (
        <CommentForm
          label="返信を書く"
          submitLabel="返信する"
          onSubmit={(body) => postReply.mutateAsync(body)}
        />
      ) : myId !== null ? (
        topic.community &&
        community.isSuccess && (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
            返信できるのはコミュニティのメンバーだけです。
            <Link
              to={`/communities/${topic.community.id}`}
              className="ml-1 font-medium text-foreground underline"
            >
              「{topic.community.name}」に参加する
            </Link>
          </p>
        )
      ) : (
        auth.status === 'anonymous' && (
          <Link
            to={`/login?redirect=${encodeURIComponent(location.pathname)}`}
            className={buttonVariants({ variant: 'outline' })}
          >
            ログインして返信する
          </Link>
        )
      )}
    </section>
  )
}
