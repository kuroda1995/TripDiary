import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button, buttonVariants } from '@/components/ui/button'
import { useAuth } from '../useAuth'

/** ヘッダーのログイン状態表示。 */
export function AuthMenu() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [loggingOut, setLoggingOut] = useState(false)

  if (auth.status === 'loading') {
    return null
  }

  if (auth.status === 'anonymous') {
    return (
      <nav aria-label="アカウント" className="flex items-center gap-2">
        <Link to="/login" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
          ログイン
        </Link>
        <Link to="/signup" className={buttonVariants({ size: 'sm' })}>
          新規登録
        </Link>
      </nav>
    )
  }

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await auth.logout()
    } finally {
      setLoggingOut(false)
      navigate('/', { replace: true })
    }
  }

  return (
    <nav aria-label="アカウント" className="flex items-center gap-2">
      <Link to="/me" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
        {auth.user.displayName}
      </Link>
      <Button variant="outline" size="sm" onClick={handleLogout} disabled={loggingOut}>
        ログアウト
      </Button>
    </nav>
  )
}
