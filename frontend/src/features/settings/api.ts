import type { Me } from '@/features/auth/types'
import { apiFetch, type AuthenticatedResponse } from '@/lib/apiClient'
import { setAccessToken } from '@/lib/authSession'

/**
 * プロフィール更新（PATCH /api/v1/users/me）。送った項目だけが変わる。
 * `avatarS3Key` は null でアイコンを削除、項目自体を送らなければ変更しない。
 */
export type ProfileUpdate = {
  displayName?: string
  bio?: string | null
  avatarS3Key?: string | null
}

export function updateProfile(update: ProfileUpdate): Promise<Me> {
  return apiFetch<Me>('/api/v1/users/me', { method: 'PATCH', body: JSON.stringify(update) })
}

/**
 * パスワード変更（PUT /api/v1/users/me/password）。
 * サーバーは他の端末のログインを切り、この端末には新しいトークンを返す（リフレッシュトークンは Cookie）。
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<Me> {
  const result = await apiFetch<AuthenticatedResponse<Me>>('/api/v1/users/me/password', {
    method: 'PUT',
    body: JSON.stringify({ currentPassword, newPassword }),
  })
  setAccessToken(result.accessToken)
  return result.user
}
