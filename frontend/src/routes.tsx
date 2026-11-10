import type { RouteObject } from 'react-router'
import { NotFoundPage } from '@/components/layout/NotFoundPage'
import { RootLayout } from '@/components/layout/RootLayout'
import { GuestOnly, RequireAuth } from '@/features/auth/components/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyPage } from '@/features/auth/pages/MyPage'
import { SignupPage } from '@/features/auth/pages/SignupPage'
import { HomePage } from '@/features/home/pages/HomePage'

/** 画面一覧は docs/requirements.md 4章を参照。機能ごとに追加していく。 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <HomePage /> },
      {
        element: <GuestOnly />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'signup', element: <SignupPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [{ path: 'me', element: <MyPage /> }],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
