import { Navigate, Outlet, useLocation, useSearchParams } from 'react-router'
import { loginPathFor, safeRedirectPath } from '../redirect'
import { useAuth } from '../useAuth'

/** ログインが必要な画面。未ログインならログイン画面へ（ログイン後に元の画面へ戻る）。 */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <p role="status" className="text-center text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (status === 'anonymous') {
    return <Navigate to={loginPathFor(location.pathname + location.search)} replace />
  }
  return <Outlet />
}

/** ログイン・新規登録画面。ログイン済みなら redirect の画面（なければトップ）へ。 */
export function GuestOnly() {
  const { status } = useAuth()
  const [searchParams] = useSearchParams()
  if (status === 'authenticated') {
    return <Navigate to={safeRedirectPath(searchParams.get('redirect'))} replace />
  }
  return <Outlet />
}
