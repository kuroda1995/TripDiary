import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import { PAGE_SIZE, spotKeys } from '@/features/spots/api'
import type { SpotPage } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'
import type { FollowListKind, FollowResult, FollowUserPage, Profile } from './types'

/** ユーザー名は大文字・小文字を区別しないため、キーは小文字にそろえる。 */
export const userKeys = {
  all: ['users', 'public'] as const,
  detail: (username: string) => [...userKeys.all, username.toLowerCase()] as const,
  profile: (username: string) => [...userKeys.detail(username), 'profile'] as const,
  list: (username: string, kind: FollowListKind) => [...userKeys.detail(username), kind] as const,
}

function withPaging(path: string, cursor: string | null): string {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return `${path}?${params}`
}

function userPath(username: string): string {
  return `/api/v1/users/${encodeURIComponent(username)}`
}

export function fetchProfile(username: string): Promise<Profile> {
  return apiFetch<Profile>(userPath(username))
}

/** フォローする（following = true）・解除する（false）。どちらも冪等。 */
export function setFollow(username: string, following: boolean): Promise<FollowResult> {
  return apiFetch<FollowResult>(`${userPath(username)}/follow`, { method: following ? 'PUT' : 'DELETE' })
}

export function fetchFollowList(
  username: string,
  kind: FollowListKind,
  cursor: string | null,
): Promise<FollowUserPage> {
  return apiFetch<FollowUserPage>(withPaging(`${userPath(username)}/${kind}`, cursor))
}

/** タイムライン（フォロー中ユーザーの公開投稿）。 */
export function fetchTimeline(cursor: string | null): Promise<SpotPage> {
  return apiFetch<SpotPage>(withPaging('/api/v1/timeline', cursor))
}

function isProfile(data: unknown): data is Profile {
  return typeof data === 'object' && data !== null && 'followerCount' in data
}

function isFollowUserPages(data: unknown): data is InfiniteData<FollowUserPage> {
  return typeof data === 'object' && data !== null && 'pages' in data
}

/**
 * 取得済みのプロフィール・一覧に、フォローの結果を反映する（画面を移動しても表示がずれないようにする）。
 *
 * @param myUsername フォローした本人（フォロー数・フォロー中一覧・タイムラインを取り直す）
 */
export function applyFollowResult(queryClient: QueryClient, result: FollowResult, myUsername: string): void {
  const target = result.username.toLowerCase()

  queryClient.setQueryData(userKeys.profile(target), (data: unknown) =>
    isProfile(data) ? { ...data, followedByMe: result.following, followerCount: result.followerCount } : data,
  )
  // 一覧を取り直すと位置が変わるため、表示中の一覧は該当する行のボタンだけを書き換える
  queryClient.setQueriesData({ queryKey: userKeys.all }, (data: unknown) => {
    if (!isFollowUserPages(data)) return data
    return {
      ...data,
      pages: data.pages.map((page) => ({
        ...page,
        items: page.items.map((user) =>
          user.username.toLowerCase() === target ? { ...user, followedByMe: result.following } : user,
        ),
      })),
    }
  })
  // 相手のフォロワー一覧・自分のプロフィール（フォロー数）とフォロー中一覧・タイムラインは次に開いたときに取り直す
  void queryClient.invalidateQueries({ queryKey: userKeys.list(target, 'followers'), refetchType: 'none' })
  void queryClient.invalidateQueries({
    queryKey: userKeys.list(myUsername, 'following'),
    refetchType: 'none',
  })
  void queryClient.invalidateQueries({ queryKey: userKeys.profile(myUsername) })
  void queryClient.invalidateQueries({ queryKey: spotKeys.timeline(), refetchType: 'none' })
}
