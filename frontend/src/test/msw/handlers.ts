import { http, HttpResponse } from 'msw'
import type { Me } from '@/features/auth/types'

export const meFixture: Me = {
  id: 1,
  email: 'taro@example.com',
  username: 'taro_trip',
  displayName: '旅するタロウ',
  bio: null,
  avatarUrl: null,
}

export function authResponse(user: Me = meFixture, accessToken = 'access-token-1') {
  return { accessToken, tokenType: 'Bearer', expiresIn: 900, user }
}

export function errorResponse(
  code: string,
  message: string,
  details: { field: string; message: string }[] = [],
) {
  return { code, message, details, requestId: 'req-test' }
}

/** 既定の振る舞い: API は正常、ログインしていない（リフレッシュトークンなし）。 */
export const defaultHandlers = [
  http.get('/actuator/health', () => HttpResponse.json({ status: 'UP' })),
  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json(errorResponse('INVALID_REFRESH_TOKEN', 'ログインの有効期限が切れました'), {
      status: 401,
    }),
  ),
]
