import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '@/test/msw/server'
import { ApiError, apiFetch, NETWORK_ERROR_CODE } from './apiClient'

describe('apiFetch', () => {
  it('成功時は JSON をパースして返し、X-Request-Id を付けて送信する', async () => {
    let sentRequestId: string | null = null
    server.use(
      http.get('/api/v1/ping', ({ request }) => {
        sentRequestId = request.headers.get('X-Request-Id')
        return HttpResponse.json({ ok: true })
      }),
    )

    const result = await apiFetch<{ ok: boolean }>('/api/v1/ping')

    expect(result).toEqual({ ok: true })
    expect(sentRequestId).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('ボディ付きのリクエストは Content-Type: application/json を付ける', async () => {
    let contentType: string | null = null
    server.use(
      http.post('/api/v1/items', ({ request }) => {
        contentType = request.headers.get('Content-Type')
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const result = await apiFetch('/api/v1/items', { method: 'POST', body: JSON.stringify({ a: 1 }) })

    expect(result).toBeUndefined()
    expect(contentType).toBe('application/json')
  })

  it('共通エラーレスポンスを ApiError に変換する', async () => {
    server.use(
      http.post('/api/v1/spots', () =>
        HttpResponse.json(
          {
            code: 'VALIDATION_ERROR',
            message: '入力内容に誤りがあります',
            details: [{ field: 'title', message: 'タイトルは必須です' }],
            requestId: 'req-from-server',
          },
          { status: 400 },
        ),
      ),
    )

    const error = await apiFetch('/api/v1/spots', { method: 'POST', body: '{}' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.code).toBe('VALIDATION_ERROR')
    expect(apiError.message).toBe('入力内容に誤りがあります')
    expect(apiError.details).toEqual([{ field: 'title', message: 'タイトルは必須です' }])
    expect(apiError.requestId).toBe('req-from-server')
  })

  it('JSON でないエラーレスポンスでも ApiError（HTTP_ステータス）になり、レスポンスヘッダの requestId を使う', async () => {
    server.use(
      http.get(
        '/api/v1/broken',
        () => new HttpResponse('Bad Gateway', { status: 502, headers: { 'X-Request-Id': 'req-header' } }),
      ),
    )

    const error = (await apiFetch('/api/v1/broken').catch((e: unknown) => e)) as ApiError

    expect(error.status).toBe(502)
    expect(error.code).toBe('HTTP_502')
    expect(error.requestId).toBe('req-header')
  })

  it('通信エラーは status 0 の NETWORK_ERROR になる', async () => {
    server.use(http.get('/api/v1/offline', () => HttpResponse.error()))

    const error = (await apiFetch('/api/v1/offline').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.code).toBe(NETWORK_ERROR_CODE)
    expect(error.requestId).not.toBeNull()
  })
})
