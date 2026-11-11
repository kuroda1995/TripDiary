import type { RouteObject } from 'react-router'
import { NotFoundPage } from '@/components/layout/NotFoundPage'
import { RootLayout } from '@/components/layout/RootLayout'
import { GuestOnly, RequireAuth } from '@/features/auth/components/RequireAuth'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { MyPage } from '@/features/auth/pages/MyPage'
import { SignupPage } from '@/features/auth/pages/SignupPage'
import { FollowListPage } from '@/features/follow/pages/FollowListPage'
import { TimelinePage } from '@/features/follow/pages/TimelinePage'
import { UserProfilePage } from '@/features/follow/pages/UserProfilePage'
import { HomePage } from '@/features/home/pages/HomePage'
import { SearchPage } from '@/features/search/pages/SearchPage'
import { MyListsRedirect } from '@/features/status/pages/MyListsRedirect'
import { PasswordSettingsPage } from '@/features/settings/pages/PasswordSettingsPage'
import { ProfileSettingsPage } from '@/features/settings/pages/ProfileSettingsPage'
import { SpotDetailPage } from '@/features/spots/pages/SpotDetailPage'
import { SpotEditPage } from '@/features/spots/pages/SpotEditPage'
import { SpotNewPage } from '@/features/spots/pages/SpotNewPage'

/** 画面一覧は docs/requirements.md 4章を参照。機能ごとに追加していく。 */
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'spots/:id', element: <SpotDetailPage /> },
      { path: 'users/:username', element: <UserProfilePage /> },
      { path: 'users/:username/following', element: <FollowListPage kind="following" /> },
      { path: 'users/:username/followers', element: <FollowListPage kind="followers" /> },
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
          { path: 'me/lists', element: <MyListsRedirect /> },
          { path: 'timeline', element: <TimelinePage /> },
          { path: 'settings/profile', element: <ProfileSettingsPage /> },
          { path: 'settings/password', element: <PasswordSettingsPage /> },
          { path: 'spots/new', element: <SpotNewPage /> },
          { path: 'spots/:id/edit', element: <SpotEditPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
