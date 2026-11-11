import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { UserAvatar } from '@/components/UserAvatar'
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
import { Button } from '@/components/ui/button'
import { formatDateTime } from '@/lib/date'
import { useDeleteComment, useEditComment, type Comment } from '../api'
import { CommentForm } from './CommentForm'

type CommentItemProps = {
  comment: Comment
  /** 自分のコメントか（編集・削除できる） */
  isMine: boolean
  /** 自分の投稿についたコメントか（削除できる） */
  isOnMySpot: boolean
}

/**
 * コメント1件。本文はプレーンテキストとして表示する（React が文字列としてエスケープするため HTML は実行されない）。
 */
export function CommentItem({ comment, isMine, isOnMySpot }: CommentItemProps) {
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const editComment = useEditComment(comment.spotId)
  const deleteComment = useDeleteComment(comment.spotId)
  const canDelete = isMine || isOnMySpot

  const handleDelete = () =>
    deleteComment.mutate(comment.id, {
      onSuccess: () => setConfirming(false),
      onError: () => {
        // ダイアログを閉じてコメントを見える状態に戻し、失敗はトーストで知らせる
        setConfirming(false)
        toast.error('コメントを削除できませんでした。時間をおいて再度お試しください')
      },
    })

  return (
    <article aria-label={`${comment.author.displayName}のコメント`} className="space-y-2 py-4">
      <header className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        <Link
          to={`/users/${comment.author.username}`}
          className="inline-flex items-center gap-1.5 font-medium hover:underline"
        >
          <UserAvatar
            displayName={comment.author.displayName}
            avatarUrl={comment.author.avatarUrl}
            size="sm"
          />
          {comment.author.displayName}
        </Link>
        <time dateTime={comment.createdAt} className="text-xs text-muted-foreground">
          {formatDateTime(comment.createdAt)}
        </time>
        {comment.edited && <span className="text-xs text-muted-foreground">（編集済み）</span>}
      </header>

      {editing ? (
        <CommentForm
          label="コメントを編集"
          submitLabel="保存する"
          defaultBody={comment.body}
          onSubmit={async (body) => {
            await editComment.mutateAsync({ commentId: comment.id, body })
            setEditing(false)
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <p className="text-sm leading-relaxed whitespace-pre-wrap">{comment.body}</p>
      )}

      {!editing && canDelete && (
        <div className="flex gap-1">
          {isMine && (
            <Button type="button" variant="ghost" size="xs" onClick={() => setEditing(true)}>
              編集
            </Button>
          )}
          {canDelete && (
            <AlertDialog open={confirming} onOpenChange={setConfirming}>
              <AlertDialogTrigger render={<Button variant="ghost" size="xs" className="text-destructive" />}>
                削除
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>コメントを削除しますか？</AlertDialogTitle>
                  <AlertDialogDescription>
                    {isMine
                      ? 'このコメントを削除します。元に戻せません。'
                      : `${comment.author.displayName}さんのコメントを、投稿者として削除します。元に戻せません。`}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>キャンセル</AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    onClick={handleDelete}
                    disabled={deleteComment.isPending}
                  >
                    {deleteComment.isPending ? '削除中…' : '削除する'}
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      )}
    </article>
  )
}
