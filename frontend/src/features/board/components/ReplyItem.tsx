import { useState } from 'react'
import { toast } from 'sonner'
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
import { useDeleteReply, type BoardReply } from '../api'
import { AuthorLink } from './AuthorLink'

type ReplyItemProps = {
  reply: BoardReply
  /** 自分の返信か */
  isMine: boolean
  /** 自分のトピックへの返信か（削除できる） */
  isOnMyTopic: boolean
  /** 自分がオーナーのコミュニティのトピックへの返信か（削除できる） */
  isInMyCommunity?: boolean
  /** トピックが属するコミュニティの ID（広場は null。削除後に一覧を取り直すため） */
  communityId?: number | null
}

/** 返信1件。本文はプレーンテキストとして表示する（React が文字列としてエスケープする）。 */
export function ReplyItem({
  reply,
  isMine,
  isOnMyTopic,
  isInMyCommunity = false,
  communityId = null,
}: ReplyItemProps) {
  const [confirming, setConfirming] = useState(false)
  const deleteReply = useDeleteReply(reply.topicId, communityId)
  const role = isOnMyTopic ? 'トピックの作成者' : 'コミュニティのオーナー'

  const handleDelete = () =>
    deleteReply.mutate(reply.id, {
      onSuccess: () => setConfirming(false),
      onError: () => {
        setConfirming(false)
        toast.error('返信を削除できませんでした。時間をおいて再度お試しください')
      },
    })

  return (
    <article aria-label={`${reply.author.displayName}の返信`} className="space-y-2 py-4">
      <header className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
        <AuthorLink author={reply.author} />
        <time dateTime={reply.createdAt} className="text-xs text-muted-foreground">
          {formatDateTime(reply.createdAt)}
        </time>
      </header>
      <p className="text-sm leading-relaxed whitespace-pre-wrap">{reply.body}</p>
      {(isMine || isOnMyTopic || isInMyCommunity) && (
        <AlertDialog open={confirming} onOpenChange={setConfirming}>
          <AlertDialogTrigger render={<Button variant="ghost" size="xs" className="text-destructive" />}>
            削除
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>返信を削除しますか？</AlertDialogTitle>
              <AlertDialogDescription>
                {isMine
                  ? 'この返信を削除します。元に戻せません。'
                  : `${reply.author.displayName}さんの返信を、${role}として削除します。元に戻せません。`}
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>キャンセル</AlertDialogCancel>
              <AlertDialogAction
                variant="destructive"
                onClick={handleDelete}
                disabled={deleteReply.isPending}
              >
                {deleteReply.isPending ? '削除中…' : '削除する'}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </article>
  )
}
