import { zodResolver } from '@hookform/resolvers/zod'
import { useId } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import type { CommunityInput } from '../api'
import { COMMUNITY_CATEGORIES, COMMUNITY_CATEGORY_LABELS, type CommunityCategory } from '../categories'
import { communitySchema, MAX_COMMUNITY_DESCRIPTION_LENGTH, type CommunityFormValues } from '../schemas'

/** コミュニティの作成フォーム（名前・説明文・カテゴリ）。 */
export function CommunityForm({ onSubmit }: { onSubmit: (input: CommunityInput) => Promise<unknown> }) {
  const descriptionId = useId()
  const categoriesErrorId = useId()
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CommunityFormValues>({
    resolver: zodResolver(communitySchema),
    defaultValues: { name: '', description: '', categories: [] },
  })
  const descriptionLength = useWatch({ control, name: 'description' }).length

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values)
    } catch (error) {
      // 同じ名前があるとき（409）は名前の欄に出す
      applyServerErrors(error, setError, ['name', 'description', 'categories'], {
        COMMUNITY_NAME_TAKEN: 'name',
      })
    }
  })

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <FormAlert message={errors.root?.server?.message} />
      <TextField
        label="コミュニティ名"
        placeholder="例: 北陸・富山のお寺巡り同好会"
        error={errors.name?.message}
        {...register('name')}
      />
      <div className="space-y-1.5">
        <Label htmlFor={descriptionId}>説明文（テーマ・目的）</Label>
        <Textarea
          id={descriptionId}
          rows={5}
          placeholder="どんな人に参加してほしいか、どんな話をしたいかを書いてください"
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={`${descriptionId}-count`}
          {...register('description')}
        />
        <div className="flex items-start justify-between gap-2">
          <div>
            {errors.description && <p className="text-xs text-destructive">{errors.description.message}</p>}
          </div>
          <p
            id={`${descriptionId}-count`}
            className={`shrink-0 text-xs ${descriptionLength > MAX_COMMUNITY_DESCRIPTION_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}
          >
            {descriptionLength} / {MAX_COMMUNITY_DESCRIPTION_LENGTH}
          </p>
        </div>
      </div>
      <Controller
        control={control}
        name="categories"
        render={({ field }) => (
          <fieldset
            className="space-y-2"
            aria-invalid={errors.categories ? true : undefined}
            aria-describedby={errors.categories ? categoriesErrorId : undefined}
          >
            <legend className="text-sm font-medium">カテゴリ（1つ以上・複数選択可）</legend>
            <div className="flex flex-wrap gap-x-5 gap-y-2">
              {COMMUNITY_CATEGORIES.map((category) => (
                <label key={category} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={field.value.includes(category)}
                    onChange={() => field.onChange(toggle(field.value, category))}
                  />
                  {COMMUNITY_CATEGORY_LABELS[category]}
                </label>
              ))}
            </div>
            {errors.categories && (
              <p id={categoriesErrorId} className="text-xs text-destructive">
                {errors.categories.message}
              </p>
            )}
          </fieldset>
        )}
      />
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? '作成中…' : 'コミュニティを作成する'}
      </Button>
    </form>
  )
}

/** 選択を切り替え、表示順に並べ直す。 */
function toggle(value: CommunityCategory[], category: CommunityCategory): CommunityCategory[] {
  return value.includes(category)
    ? value.filter((c) => c !== category)
    : COMMUNITY_CATEGORIES.filter((c) => c === category || value.includes(c))
}
