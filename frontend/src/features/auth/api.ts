import { ApiError, apiFetch, refreshAccessToken, type AuthenticatedResponse } from '@/lib/apiClient'
import { setAccessToken } from '@/lib/authSession'
import type { GuestCredentials, LoginInput, Me, SignupInput } from './types'

async function authenticate(path: string, body: unknown): Promise<Me> {
  const result = await apiFetch<AuthenticatedResponse<Me>>(path, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  setAccessToken(result.accessToken)
  return result.user
}

export function signup(input: SignupInput): Promise<Me> {
  return authenticate('/api/v1/auth/signup', input)
}

export function login(input: LoginInput): Promise<Me> {
  return authenticate('/api/v1/auth/login', input)
}

/** ゲストのログイン情報。ゲストログインを使えない（404 GUEST_LOGIN_DISABLED）ときは null。 */
export async function fetchGuestCredentials(): Promise<GuestCredentials | null> {
  try {
    return await apiFetch<GuestCredentials>('/api/v1/auth/guest')
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null
    throw error
  }
}

/** リフレッシュトークン（Cookie）からログイン状態を復元する。 */
export async function restoreSession(): Promise<Me> {
  const result = await refreshAccessToken<Me>()
  return result.user
}

/**
 * ログアウトする。サーバー側の失効に失敗しても（通信エラー等）、この端末ではログアウト状態にするため例外は投げない。
 * （失効できなかったリフレッシュトークンも有効期限の14日で無効になる）
 */
export async function logout(): Promise<void> {
  try {
    await apiFetch<void>('/api/v1/auth/logout', { method: 'POST' })
  } catch {
    // 端末側のログアウトを優先する
  } finally {
    setAccessToken(null)
  }
}

export function fetchMe(): Promise<Me> {
  return apiFetch<Me>('/api/v1/users/me')
}
