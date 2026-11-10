import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { applyServerErrors } from '../applyServerErrors'
import { safeRedirectPath } from '../redirect'
import { signupSchema, type SignupFormValues } from '../schemas'
import { useAuth } from '../useAuth'

const FIELDS = ['email', 'username', 'displayName', 'password'] as const

export function SignupPage() {
  const { signup } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirectTo = safeRedirectPath(searchParams.get('redirect'))

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: '', username: '', displayName: '', password: '', passwordConfirmation: '' },
  })

  const onSubmit = handleSubmit(async ({ email, username, displayName, password }) => {
    try {
      await signup({ email, username, displayName, password })
      navigate(redirectTo, { replace: true })
    } catch (error) {
      applyServerErrors(error, setError, FIELDS, {
        EMAIL_ALREADY_EXISTS: 'email',
        USERNAME_ALREADY_EXISTS: 'username',
      })
    }
  })

  return (
    <div className="mx-auto max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl font-bold">新規登録</h1>
          </CardTitle>
          <CardDescription>アカウントを作成して、旅の記録をはじめましょう。</CardDescription>
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
              label="ユーザー名"
              autoComplete="username"
              description="半角英数字と _ で3〜30文字。プロフィールの URL に使われ、あとから変更できません。"
              error={errors.username?.message}
              {...register('username')}
            />
            <TextField
              label="表示名"
              autoComplete="nickname"
              error={errors.displayName?.message}
              {...register('displayName')}
            />
            <TextField
              label="パスワード"
              type="password"
              autoComplete="new-password"
              description="8〜72文字の半角英数字・記号。英字と数字をそれぞれ1文字以上含めてください。"
              error={errors.password?.message}
              {...register('password')}
            />
            <TextField
              label="パスワード（確認）"
              type="password"
              autoComplete="new-password"
              error={errors.passwordConfirmation?.message}
              {...register('passwordConfirmation')}
            />
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? '登録中…' : '登録する'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            すでにアカウントをお持ちの方は{' '}
            <Link
              to={`/login?redirect=${encodeURIComponent(redirectTo)}`}
              className="text-primary underline-offset-4 hover:underline"
            >
              ログイン
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
