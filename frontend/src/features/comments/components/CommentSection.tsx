import { useInfiniteQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { commentKeys, fetchComments, usePostComment } from '../api'
import { CommentForm } from './CommentForm'
import { CommentItem } from './CommentItem'

type CommentSectionProps = {
  spotId: number
  spotOwnerId: number
  commentCount: number
}

/** スポット詳細のコメント欄（古い順・「さらに表示」で続きを読み込む）。 */
export function CommentSection({ spotId, spotOwnerId, commentCount }: CommentSectionProps) {
  const auth = useAuth()
  const location = useLocation()
  const postComment = usePostComment(spotId)
  const query = useInfiniteQuery({
    queryKey: commentKeys.list(spotId),
    queryFn: ({ pageParam }) => fetchComments(spotId, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    // 本人の非公開スポットはログインしていないと 404 になるため、ログイン状態の確認を待つ
    enabled: auth.status !== 'loading',
  })
  const me = auth.status === 'authenticated' ? auth.user : null
  const comments = query.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section aria-labelledby="comments-heading" className="space-y-4 border-t pt-6">
      <h2 id="comments-heading" className="text-lg font-bold">
        コメント（{commentCount}件）
      </h2>

      {query.isPending && (
        <p role="status" className="text-sm text-muted-foreground">
          読み込み中…
        </p>
      )}
      {query.isError && <p className="text-sm text-destructive">コメントを読み込めませんでした。</p>}
      {query.isSuccess && comments.length === 0 && (
        <p className="text-sm text-muted-foreground">まだコメントはありません。</p>
      )}
      {comments.length > 0 && (
        <ol aria-label="コメント" className="divide-y">
          {comments.map((comment) => (
            <li key={comment.id}>
              <CommentItem
                comment={comment}
                isMine={me !== null && me.id === comment.author.id}
                isOnMySpot={me !== null && me.id === spotOwnerId}
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

      {me ? (
        <CommentForm
          label="コメントを書く"
          submitLabel="コメントする"
          onSubmit={(body) => postComment.mutateAsync(body)}
        />
      ) : (
        auth.status === 'anonymous' && (
          <Link
            to={`/login?redirect=${encodeURIComponent(location.pathname)}`}
            className={buttonVariants({ variant: 'outline' })}
          >
            ログインしてコメントする
          </Link>
        )
      )}
    </section>
  )
}
