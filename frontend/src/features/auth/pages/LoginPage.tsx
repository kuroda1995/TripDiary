import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { FormAlert } from '@/components/form/FormAlert'
import { TextField } from '@/components/form/TextField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { applyServerErrors } from '../applyServerErrors'
import { safeRedirectPath } from '../redirect'
import { loginSchema, type LoginFormValues } from '../schemas'
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

  return (
    <div className="mx-auto max-w-sm">
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
