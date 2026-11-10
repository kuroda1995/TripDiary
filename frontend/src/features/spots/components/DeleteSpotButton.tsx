import { useState } from 'react'
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
import { useDeleteSpot } from '../api'

type DeleteSpotButtonProps = {
  spotId: number
  spotTitle: string
  onDeleted: () => void
}

/** 確認ダイアログ付きの削除ボタン。 */
export function DeleteSpotButton({ spotId, spotTitle, onDeleted }: DeleteSpotButtonProps) {
  const [open, setOpen] = useState(false)
  const deleteSpot = useDeleteSpot(spotId)

  const handleDelete = () =>
    deleteSpot.mutate(undefined, {
      onSuccess: () => {
        setOpen(false)
        onDeleted()
      },
    })

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={<Button variant="destructive" size="sm" />}>削除</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>スポットを削除しますか？</AlertDialogTitle>
          <AlertDialogDescription>
            「{spotTitle}」を削除します。ついた「行ってみたい！」やコメントも削除され、元に戻せません。
          </AlertDialogDescription>
        </AlertDialogHeader>
        {deleteSpot.isError && (
          <p role="alert" className="text-sm text-destructive">
            削除できませんでした。時間をおいて再度お試しください。
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel>キャンセル</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={handleDelete} disabled={deleteSpot.isPending}>
            {deleteSpot.isPending ? '削除中…' : '削除する'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
