import { MessagesSquare, Search } from 'lucide-react'
import { Link, Outlet } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { Toaster } from 'sonner'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { AuthMenu } from '@/features/auth/components/AuthMenu'
import { ApiStatusBadge } from '@/features/system/components/ApiStatusBadge'

export function RootLayout() {
  return (
    <AuthProvider>
      <div className="flex min-h-svh flex-col bg-background">
        <header className="border-b">
          <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between px-4">
            <div className="flex items-center gap-2">
              <Link to="/" className="text-lg font-bold">
                TripDiary
              </Link>
              <nav aria-label="メイン" className="flex items-center gap-1">
                <Link to="/search" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
                  <Search aria-hidden />
                  探す
                </Link>
                <Link to="/community" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
                  <MessagesSquare aria-hidden />
                  {/* 狭い画面ではアイコンだけにする（読み上げ名は常に「コミュニティ広場」） */}
                  <span className="sr-only sm:not-sr-only">コミュニティ広場</span>
                </Link>
              </nav>
            </div>
            <AuthMenu />
          </div>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
          <Outlet />
        </main>
        <Toaster position="bottom-center" richColors closeButton />
        <footer className="border-t">
          <div className="mx-auto flex h-12 w-full max-w-5xl items-center justify-end px-4">
            <ApiStatusBadge />
          </div>
        </footer>
      </div>
    </AuthProvider>
  )
}
