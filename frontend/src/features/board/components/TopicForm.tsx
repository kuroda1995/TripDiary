import { zodResolver } from '@hookform/resolvers/zod'
import { useId } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import type { TopicInput } from '../api'
import { MAX_TOPIC_BODY_LENGTH, topicSchema, type TopicFormValues } from '../schemas'

/** トピックの作成フォーム。 */
export function TopicForm({ onSubmit }: { onSubmit: (input: TopicInput) => Promise<unknown> }) {
  const bodyId = useId()
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TopicFormValues>({ resolver: zodResolver(topicSchema), defaultValues: { title: '', body: '' } })
  const bodyLength = useWatch({ control, name: 'body' }).length

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values)
    } catch (error) {
      applyServerErrors(error, setError, ['title', 'body'])
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <FormAlert message={errors.root?.server?.message} />
      <TextField
        label="タイトル"
        placeholder="例: 紅葉の時期におすすめのお寺を教えてください"
        error={errors.title?.message}
        {...register('title')}
      />
      <div className="space-y-1.5">
        <Label htmlFor={bodyId}>本文</Label>
        <Textarea
          id={bodyId}
          rows={5}
          placeholder="相談したいこと・共有したい情報を書いてください"
          aria-invalid={errors.body ? true : undefined}
          aria-describedby={`${bodyId}-count`}
          {...register('body')}
        />
        <div className="flex items-start justify-between gap-2">
          <div>{errors.body && <p className="text-xs text-destructive">{errors.body.message}</p>}</div>
          <p
            id={`${bodyId}-count`}
            className={`shrink-0 text-xs ${bodyLength > MAX_TOPIC_BODY_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}
          >
            {bodyLength} / {MAX_TOPIC_BODY_LENGTH}
          </p>
        </div>
      </div>
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? '作成中…' : 'トピックを作成する'}
      </Button>
    </form>
  )
}
