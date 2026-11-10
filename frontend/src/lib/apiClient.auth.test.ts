import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { authResponse, errorResponse } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { ApiError, apiFetch, refreshAccessToken } from './apiClient'
import { getAccessToken, onSessionExpired, setAccessToken } from './authSession'

const expired = () =>
  HttpResponse.json(errorResponse('TOKEN_EXPIRED', 'アクセストークンの有効期限が切れています'), {
    status: 401,
  })

describe('apiFetch（認証）', () => {
  afterEach(() => vi.restoreAllMocks())

  it('ログイン中は Authorization ヘッダにアクセストークンを付ける', async () => {
    setAccessToken('token-a')
    let authorization: string | null = null
    server.use(
      http.get('/api/v1/users/me', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ ok: true })
      }),
    )

    await apiFetch('/api/v1/users/me')

    expect(authorization).toBe('Bearer token-a')
  })

  it('認証 API にはアクセストークンを付けない（期限切れトークンでトークン更新を妨げない）', async () => {
    setAccessToken('stale')
    let authorization: string | null = 'not-called'
    server.use(
      http.post('/api/v1/auth/login', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json(authResponse())
      }),
    )

    await apiFetch('/api/v1/auth/login', { method: 'POST', body: '{}' })

    expect(authorization).toBeNull()
  })

  it('TOKEN_EXPIRED のときはトークンを更新して1回だけリトライする', async () => {
    setAccessToken('old-token')
    const seen: (string | null)[] = []
    server.use(
      http.get('/api/v1/users/me', ({ request }) => {
        const auth = request.headers.get('Authorization')
        seen.push(auth)
        return auth === 'Bearer new-token' ? HttpResponse.json({ ok: true }) : expired()
      }),
      http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse(undefined, 'new-token'))),
    )

    const result = await apiFetch<{ ok: boolean }>('/api/v1/users/me')

    expect(result).toEqual({ ok: true })
    expect(seen).toEqual(['Bearer old-token', 'Bearer new-token'])
    expect(getAccessToken()).toBe('new-token')
  })

  it('同時に複数のリクエストが期限切れになっても、トークン更新は1回だけ行う', async () => {
    setAccessToken('old-token')
    let refreshCalls = 0
    server.use(
      http.get('/api/v1/items/:id', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new-token'
          ? HttpResponse.json({ ok: true })
          : expired(),
      ),
      http.post('/api/v1/auth/refresh', async () => {
        refreshCalls++
        await new Promise((resolve) => setTimeout(resolve, 10))
        return HttpResponse.json(authResponse(undefined, 'new-token'))
      }),
    )

    await Promise.all([apiFetch('/api/v1/items/1'), apiFetch('/api/v1/items/2'), apiFetch('/api/v1/items/3')])

    expect(refreshCalls).toBe(1)
  })

  it('トークン更新にも失敗したらログイン切れを通知し、元のエラーを投げる', async () => {
    setAccessToken('old-token')
    const listener = vi.fn<() => void>()
    const unsubscribe = onSessionExpired(listener)
    server.use(http.get('/api/v1/users/me', expired))

    const error = (await apiFetch('/api/v1/users/me').catch((e: unknown) => e)) as ApiError

    expect(error.code).toBe('TOKEN_EXPIRED')
    expect(listener).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
    unsubscribe()
  })

  it('期限切れ以外の 401（INVALID_TOKEN など）はリトライしない', async () => {
    let refreshCalls = 0
    server.use(
      http.get('/api/v1/users/me', () =>
        HttpResponse.json(errorResponse('INVALID_TOKEN', '不正'), { status: 401 }),
      ),
      http.post('/api/v1/auth/refresh', () => {
        refreshCalls++
        return HttpResponse.json(authResponse())
      }),
    )

    await expect(apiFetch('/api/v1/users/me')).rejects.toMatchObject({ code: 'INVALID_TOKEN' })
    expect(refreshCalls).toBe(0)
  })

  it('refreshAccessToken は新しいアクセストークンを保存してユーザーを返す', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse(undefined, 'fresh'))))

    const result = await refreshAccessToken()

    expect(result.accessToken).toBe('fresh')
    expect(getAccessToken()).toBe('fresh')
  })
})
