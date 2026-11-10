import { zodResolver } from '@hookform/resolvers/zod'
import { useId } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import { PhotoUploader } from '@/features/photos/components/PhotoUploader'
import { todayInJapan } from '@/lib/date'
import {
  MAX_AREA_TAGS,
  MAX_IMPRESSION_LENGTH,
  spotSchema,
  toSpotInput,
  type SpotFormValues,
} from '../schemas'
import type { SpotInput } from '../types'
import { AreaTagPicker } from './AreaTagPicker'

const FIELDS = [
  'title',
  'locationName',
  'areaTagIds',
  'impression',
  'visitedOn',
  'visibility',
  'photos',
] as const

type SpotFormProps = {
  defaultValues: SpotFormValues
  submitLabel: string
  onSubmit: (input: SpotInput) => Promise<unknown>
}

/** スポットの作成・編集フォーム。 */
export function SpotForm({ defaultValues, submitLabel, onSubmit }: SpotFormProps) {
  const impressionId = useId()
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SpotFormValues>({ resolver: zodResolver(spotSchema), defaultValues })

  const impressionLength = useWatch({ control, name: 'impression' }).length
  const isUploading = useWatch({ control, name: 'photos' }).some(
    (photo) => photo.status === 'preparing' || photo.status === 'uploading',
  )

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(toSpotInput(values))
    } catch (error) {
      applyServerErrors(error, setError, FIELDS, {
        AREA_TAG_NOT_FOUND: 'areaTagIds',
        DUPLICATE_AREA_TAG: 'areaTagIds',
        VISITED_ON_IN_FUTURE: 'visitedOn',
        TOO_MANY_PHOTOS: 'photos',
        DUPLICATE_PHOTO_ORDER: 'photos',
        INVALID_PHOTO_KEY: 'photos',
      })
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-6">
      <FormAlert message={errors.root?.server?.message} />
      <TextField
        label="タイトル"
        placeholder="例: 朝の清水寺"
        error={errors.title?.message}
        {...register('title')}
      />
      <TextField
        label="場所"
        placeholder="例: 清水寺（京都府京都市東山区）"
        error={errors.locationName?.message}
        {...register('locationName')}
      />
      <Controller
        control={control}
        name="areaTagIds"
        render={({ field, fieldState }) => (
          <AreaTagPicker
            value={field.value}
            onChange={field.onChange}
            max={MAX_AREA_TAGS}
            error={fieldState.error?.message}
          />
        )}
      />
      <TextField
        label="訪問日（任意）"
        type="date"
        max={todayInJapan()}
        className="w-auto"
        error={errors.visitedOn?.message}
        {...register('visitedOn')}
      />
      <div className="space-y-1.5">
        <Label htmlFor={impressionId}>感想（任意）</Label>
        <Textarea
          id={impressionId}
          rows={6}
          aria-invalid={errors.impression ? true : undefined}
          aria-describedby={`${impressionId}-count`}
          {...register('impression')}
        />
        <p
          id={`${impressionId}-count`}
          className={`text-right text-xs ${impressionLength > MAX_IMPRESSION_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}
        >
          {impressionLength} / {MAX_IMPRESSION_LENGTH}
        </p>
        {errors.impression && <p className="text-xs text-destructive">{errors.impression.message}</p>}
      </div>
      <Controller
        control={control}
        name="photos"
        render={({ field, fieldState }) => (
          <PhotoUploader value={field.value} onChange={field.onChange} error={fieldState.error?.message} />
        )}
      />
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">公開範囲</legend>
        <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
          <label className="flex items-center gap-2">
            <input
              type="radio"
              value="PUBLIC"
              className="size-4 accent-primary"
              {...register('visibility')}
            />
            公開（全員が見られる）
          </label>
          <label className="flex items-center gap-2">
            <input
              type="radio"
              value="PRIVATE"
              className="size-4 accent-primary"
              {...register('visibility')}
            />
            非公開（自分だけ）
          </label>
        </div>
      </fieldset>
      <Button type="submit" disabled={isSubmitting || isUploading}>
        {isSubmitting ? '保存中…' : isUploading ? '写真をアップロード中…' : submitLabel}
      </Button>
    </form>
  )
}
