import type { RouteObject } from 'react-router'
import { NotFoundPage } from '@/components/layout/NotFoundPage'
import { RootLayout } from '@/components/layout/RootLayout'
import { GuestOnly, RequireAuth } from '@/features/auth/components/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyPage } from '@/features/auth/pages/MyPage'
import { SignupPage } from '@/features/auth/pages/SignupPage'
import { HomePage } from '@/features/home/pages/HomePage'
import { SpotDetailPage } from '@/features/spots/pages/SpotDetailPage'
import { SpotEditPage } from '@/features/spots/pages/SpotEditPage'
import { SpotNewPage } from '@/features/spots/pages/SpotNewPage'
import { UserSpotsPage } from '@/features/spots/pages/UserSpotsPage'

/** 画面一覧は docs/requirements.md 4章を参照。機能ごとに追加していく。 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'spots/:id', element: <SpotDetailPage /> },
      { path: 'users/:username', element: <UserSpotsPage /> },
      {
        element: <GuestOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'signup', element: <SignupPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: 'me', element: <MyPage /> },
          { path: 'spots/new', element: <SpotNewPage /> },
          { path: 'spots/:id/edit', element: <SpotEditPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
