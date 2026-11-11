import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { applyServerErrors } from '@/features/auth/applyServerErrors'
import { useAuth } from '@/features/auth/useAuth'
import { changePassword } from '../api'
import { passwordSchema, type PasswordFormValues } from '../schemas'

const FIELDS = ['currentPassword', 'newPassword'] as const

const EMPTY: PasswordFormValues = { currentPassword: '', newPassword: '', newPasswordConfirmation: '' }

/** パスワード変更（/settings/password。ログイン必須）。 */
export function PasswordSettingsPage() {
  const { updateUser } = useAuth()
  const [done, setDone] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordFormValues>({ resolver: zodResolver(passwordSchema), defaultValues: EMPTY })

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setDone(false)
    try {
      updateUser(await changePassword(currentPassword, newPassword))
      reset(EMPTY)
      setDone(true)
    } catch (error) {
      applyServerErrors(error, setError, FIELDS, {
        INVALID_CURRENT_PASSWORD: 'currentPassword',
        PASSWORD_UNCHANGED: 'newPassword',
      })
    }
  })

  return (
    <div className="mx-auto max-w-sm space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-bold">パスワード変更</h1>
          </CardTitle>
          <CardDescription>変更すると、この端末以外ではログアウトされます。</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <FormAlert message={errors.root?.server?.message} />
            {done && (
              <p role="status" className="rounded-md bg-muted px-3 py-2 text-sm">
                パスワードを変更しました。
              </p>
            )}
            <TextField
              label="現在のパスワード"
              type="password"
              autoComplete="current-password"
              error={errors.currentPassword?.message}
              {...register('currentPassword')}
            />
            <TextField
              label="新しいパスワード"
              type="password"
              autoComplete="new-password"
              description="8〜72文字の半角英数字・記号。英字と数字をそれぞれ1文字以上含めてください。"
              error={errors.newPassword?.message}
              {...register('newPassword')}
            />
            <TextField
              label="新しいパスワード（確認）"
              type="password"
              autoComplete="new-password"
              error={errors.newPasswordConfirmation?.message}
              {...register('newPasswordConfirmation')}
            />
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? '変更中…' : '変更する'}
            </Button>
          </form>
        </CardContent>
      </Card>
      <p className="text-sm">
        <Link to="/settings/profile" className="text-primary underline-offset-4 hover:underline">
          プロフィール編集に戻る
        </Link>
      </p>
    </div>
  )
}
