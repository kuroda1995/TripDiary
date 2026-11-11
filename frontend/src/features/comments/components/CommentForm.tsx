import { zodResolver } from '@hookform/resolvers/zod'
import { useId } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { FormAlert } from '@/components/form/FormAlert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import { commentSchema, MAX_COMMENT_LENGTH, type CommentFormValues } from '../schemas'

type CommentFormProps = {
  label: string
  submitLabel: string
  defaultBody?: string
  onSubmit: (body: string) => Promise<unknown>
  onCancel?: () => void
}

/** コメントの入力欄（新規投稿・編集で共通。残り文字数を表示する）。 */
export function CommentForm({ label, submitLabel, defaultBody = '', onSubmit, onCancel }: CommentFormProps) {
  const id = useId()
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CommentFormValues>({
    resolver: zodResolver(commentSchema),
    defaultValues: { body: defaultBody },
  })
  const length = useWatch({ control, name: 'body' }).length

  const submit = handleSubmit(async ({ body }) => {
    try {
      await onSubmit(body)
      reset({ body: '' })
    } catch (error) {
      applyServerErrors(error, setError, ['body'])
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-2">
      <FormAlert message={errors.root?.server?.message} />
      <Label htmlFor={id}>{label}</Label>
      <Textarea
        id={id}
        rows={3}
        aria-invalid={errors.body ? true : undefined}
        aria-describedby={`${id}-count`}
        {...register('body')}
      />
      <div className="flex items-start justify-between gap-2">
        <div>{errors.body && <p className="text-xs text-destructive">{errors.body.message}</p>}</div>
        <p
          id={`${id}-count`}
          className={`shrink-0 text-xs ${length > MAX_COMMENT_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}
        >
          残り {MAX_COMMENT_LENGTH - length} 文字
        </p>
      </div>
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={isSubmitting}>
          {isSubmitting ? '送信中…' : submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" size="sm" variant="ghost" onClick={onCancel}>
            キャンセル
          </Button>
        )}
      </div>
    </form>
  )
}
