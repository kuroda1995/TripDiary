import { Link, Outlet } from 'react-router'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { AuthMenu } from '@/features/auth/components/AuthMenu'
import { ApiStatusBadge } from '@/features/system/components/ApiStatusBadge'

export function RootLayout() {
  return (
    <AuthProvider>
      <div className="flex min-h-svh flex-col bg-background">
        <header className="border-b">
          <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
            <Link to="/" className="text-lg font-bold">
              TripDiary
            </Link>
            <AuthMenu />
          </div>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
          <Outlet />
        </main>
        <footer className="border-t">
          <div className="mx-auto flex h-12 w-full max-w-5xl items-center justify-end px-4">
            <ApiStatusBadge />
          </div>
        </footer>
      </div>
    </AuthProvider>
  )
}
