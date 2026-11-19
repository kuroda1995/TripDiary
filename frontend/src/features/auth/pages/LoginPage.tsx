import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { fetchGuestCredentials } from '../api'
import { applyServerErrors } from '../applyServerErrors'
import { safeRedirectPath } from '../redirect'
import { loginSchema, type LoginFormValues } from '../schemas'
import type { GuestCredentials } from '../types'
import { useAuth } from '../useAuth'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirectTo = safeRedirectPath(searchParams.get('redirect'))

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      await login(values)
      navigate(redirectTo, { replace: true })
    } catch (error) {
      applyServerErrors(error, setError, ['email', 'password'])
    }
  })

  /** ゲストのログイン情報をフォームに入れる（自分でログインボタンを押してもらう）。 */
  const fillGuestCredentials = ({ email, password }: GuestCredentials) => {
    setValue('email', email, { shouldValidate: true })
    setValue('password', password, { shouldValidate: true })
  }

  return (
    <div className="mx-auto max-w-sm space-y-4">
      <GuestLoginCard
        onLoggedIn={() => navigate(redirectTo, { replace: true })}
        onFill={fillGuestCredentials}
      />
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-bold">ログイン</h1>
          </CardTitle>
          <CardDescription>メールアドレスとパスワードでログインします。</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="space-y-4">
            <FormAlert message={errors.root?.server?.message} />
            <TextField
              label="メールアドレス"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register('email')}
            />
            <TextField
              label="パスワード"
              type="password"
              autoComplete="current-password"
              error={errors.password?.message}
              {...register('password')}
            />
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'ログイン中…' : 'ログイン'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            アカウントをお持ちでない方は{' '}
            <Link
              to={`/signup?redirect=${encodeURIComponent(redirectTo)}`}
              className="text-primary underline-offset-4 hover:underline"
            >
              新規登録
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

type GuestLoginCardProps = {
  onLoggedIn: () => void
  onFill: (credentials: GuestCredentials) => void
}

/**
 * ゲストログイン（docs/features/01_auth.md 4.8）。採用担当者などが個人情報を登録せずに試せるよう、
 * サンプルデータのユーザーでワンクリックでログインできる。ゲストログインが無効な環境では何も表示しない。
 */
function GuestLoginCard({ onLoggedIn, onFill }: GuestLoginCardProps) {
  const { login } = useAuth()
  const { data: credentials } = useQuery({
    queryKey: ['auth', 'guest'],
    queryFn: fetchGuestCredentials,
    staleTime: Infinity,
    retry: false,
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  if (!credentials) return null

  const loginAsGuest = async () => {
    setSubmitting(true)
    setError(undefined)
    try {
      await login(credentials)
      onLoggedIn()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'ログインできませんでした')
      setSubmitting(false)
    }
  }

  return (
    <Card className="border-primary/40">
      <CardHeader>
        <CardTitle>
          <h2 className="text-lg font-bold">採用担当者様・面接官の方へ</h2>
        </CardTitle>
        <CardDescription>
          個人情報を登録せずに、サンプルデータの入ったデモ用アカウントですぐにお試しいただけます。
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormAlert message={error} />
        <Button type="button" className="w-full" onClick={loginAsGuest} disabled={submitting}>
          {submitting ? 'ログイン中…' : 'ゲストとしてログイン（採用担当者様用）'}
        </Button>
        <div className="space-y-2 rounded-md bg-muted px-3 py-2 text-sm">
          <p className="text-muted-foreground">フォームから入力する場合はこちらをお使いください。</p>
          <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
            <dt className="text-muted-foreground">メールアドレス</dt>
            <dd className="font-mono break-all">{credentials.email}</dd>
            <dt className="text-muted-foreground">パスワード</dt>
            <dd className="font-mono break-all">{credentials.password}</dd>
          </dl>
          <Button type="button" variant="outline" size="sm" onClick={() => onFill(credentials)}>
            フォームに入力する
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          ゲストアカウントは複数の方が使うため、パスワードは変更できません。
        </p>
      </CardContent>
    </Card>
  )
}
