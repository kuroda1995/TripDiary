import { Link, Outlet } from 'react-router'
import { ApiStatusBadge } from '@/features/system/components/ApiStatusBadge'

export function RootLayout() {
  return (
    <div className="min-h-svh bg-background">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/" className="text-lg font-bold">
            TripDiary
          </Link>
          <ApiStatusBadge />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
