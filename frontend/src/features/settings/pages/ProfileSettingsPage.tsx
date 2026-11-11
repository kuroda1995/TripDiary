import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import type { Me } from '@/features/auth/types'
import { useAuth } from '@/features/auth/useAuth'
import { updateProfile, type ProfileUpdate } from '../api'
import { AvatarField, type AvatarValue } from '../components/AvatarField'
import { MAX_BIO_LENGTH, profileSchema, type ProfileFormValues } from '../schemas'

const FIELDS = ['displayName', 'bio'] as const

/** プロフィール編集（/settings/profile。ログイン必須）。 */
export function ProfileSettingsPage() {
  const auth = useAuth()
  // RequireAuth の内側なので必ずログイン済み
  if (auth.status !== 'authenticated') return null
  return <ProfileForm user={auth.user} onSaved={auth.updateUser} />
}

function ProfileForm({ user, onSaved }: { user: Me; onSaved: (user: Me) => void }) {
  const bioId = useId()
  const [avatar, setAvatar] = useState<AvatarValue>({ s3Key: undefined, previewUrl: user.avatarUrl })
  const [avatarUploading, setAvatarUploading] = useState(false)
  const [saved, setSaved] = useState(false)

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { displayName: user.displayName, bio: user.bio ?? '' },
  })
  const displayName = useWatch({ control, name: 'displayName' })
  const bioLength = useWatch({ control, name: 'bio' }).length

  const onSubmit = handleSubmit(async (values) => {
    setSaved(false)
    const update: ProfileUpdate = {
      displayName: values.displayName,
      bio: values.bio.trim() === '' ? null : values.bio,
    }
    if (avatar.s3Key !== undefined) update.avatarS3Key = avatar.s3Key
    try {
      const updated = await onSave(update)
      // 保存したアイコンを「現在のアイコン」として扱う（もう一度保存しても送らない）
      setAvatar({ s3Key: undefined, previewUrl: updated.avatarUrl })
      setSaved(true)
    } catch (error) {
      applyServerErrors(error, setError, FIELDS)
    }
  })

  const onSave = async (update: ProfileUpdate) => {
    const updated = await updateProfile(update)
    onSaved(updated)
    return updated
  }

  return (
    <div className="mx-auto max-w-lg space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-bold">プロフィール編集</h1>
          </CardTitle>
          <CardDescription>
            表示名・自己紹介・アイコンは、投稿者として他のユーザーにも表示されます。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="space-y-5">
            <FormAlert message={errors.root?.server?.message} />
            {saved && (
              <p role="status" className="rounded-md bg-muted px-3 py-2 text-sm">
                プロフィールを保存しました。
              </p>
            )}
            <AvatarField
              displayName={displayName}
              value={avatar}
              onChange={(next) => {
                setSaved(false)
                setAvatar(next)
              }}
              onUploadingChange={setAvatarUploading}
            />
            <TextField
              label="表示名"
              autoComplete="nickname"
              error={errors.displayName?.message}
              {...register('displayName')}
            />
            <div className="space-y-1.5">
              <Label htmlFor={bioId}>自己紹介（任意）</Label>
              <Textarea
                id={bioId}
                rows={4}
                aria-invalid={errors.bio ? true : undefined}
                aria-describedby={`${bioId}-count`}
                {...register('bio')}
              />
              <p
                id={`${bioId}-count`}
                className={`text-right text-xs ${bioLength > MAX_BIO_LENGTH ? 'text-destructive' : 'text-muted-foreground'}`}
              >
                {bioLength} / {MAX_BIO_LENGTH}
              </p>
              {errors.bio && <p className="text-xs text-destructive">{errors.bio.message}</p>}
            </div>
            <Button type="submit" disabled={isSubmitting || avatarUploading}>
              {isSubmitting ? '保存中…' : avatarUploading ? '画像をアップロード中…' : '保存する'}
            </Button>
          </form>
        </CardContent>
      </Card>
      <p className="text-sm">
        <Link to="/settings/password" className="text-primary underline-offset-4 hover:underline">
          パスワードを変更する
        </Link>
      </p>
    </div>
  )
}
