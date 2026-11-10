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

export const areasFixture = [
  {
    id: 1,
    name: '関西',
    slug: 'kansai',
    level: 1,
    children: [
      { id: 11, name: '京都', slug: 'kyoto', level: 2, children: [] },
      { id: 12, name: '大阪', slug: 'osaka', level: 2, children: [] },
      { id: 13, name: '兵庫', slug: 'hyogo', level: 2, children: [] },
    ],
  },
  {
    id: 2,
    name: '九州・沖縄',
    slug: 'kyushu-okinawa',
    level: 1,
    children: [
      { id: 21, name: '福岡', slug: 'fukuoka', level: 2, children: [] },
      { id: 22, name: '長崎', slug: 'nagasaki', level: 2, children: [] },
      { id: 23, name: '沖縄', slug: 'okinawa', level: 2, children: [] },
    ],
  },
]

export const otherUser = { id: 2, username: 'hanako', displayName: 'はなこ', avatarUrl: null }

export function spotFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 100,
    title: '朝の清水寺',
    locationName: '清水寺（京都府京都市東山区）',
    areaTags: [{ id: 11, name: '京都', slug: 'kyoto' }],
    impression: '開門直後に行くと人が少なくて最高でした。',
    visitedOn: '2026-10-03',
    visibility: 'PUBLIC',
    photos: [],
    author: {
      id: meFixture.id,
      username: meFixture.username,
      displayName: meFixture.displayName,
      avatarUrl: null,
    },
    createdAt: '2026-10-04T09:00:00Z',
    updatedAt: '2026-10-04T09:00:00Z',
    ...overrides,
  }
}

export function spotSummaryFixture(id: number, title: string, overrides: Record<string, unknown> = {}) {
  const {
    impression,
    updatedAt: _updatedAt,
    photos: _photos,
    ...rest
  } = spotFixture({ id, title, ...overrides })
  return { coverPhotoUrl: null, ...rest, impressionExcerpt: impression }
}

/** 写真アップロードのテスト用: 署名付き URL 発行と S3 への PUT。 */
export const STORAGE_ORIGIN = 'http://storage.test'

/**
 * S3 バケットの CORS 設定の代わり。
 * ブラウザ（jsdom）は別オリジンへの PUT の前にプリフライト（OPTIONS）を送り、許可されないと PUT しない。
 */
export const STORAGE_CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'PUT',
  'Access-Control-Allow-Headers': 'content-type',
}

/** S3 への PUT の応答を差し替える（uploadHandlers より後に server.use すると優先される）。 */
export function storagePutHandler(resolver: () => Promise<number> | number) {
  return http.put(`${STORAGE_ORIGIN}/tripdiary-photos/*`, async () => {
    const status = await resolver()
    return new HttpResponse(null, { status, headers: STORAGE_CORS_HEADERS })
  })
}

export function uploadHandlers(onPut: (key: string, contentType: string | null) => void = () => {}) {
  let counter = 0
  return [
    http.options(
      `${STORAGE_ORIGIN}/tripdiary-photos/*`,
      () => new HttpResponse(null, { status: 200, headers: STORAGE_CORS_HEADERS }),
    ),
    http.post('/api/v1/uploads/presigned-url', () => {
      counter += 1
      const s3Key = `spots/1/0000000${counter}.jpg`
      return HttpResponse.json({
        uploadUrl: `${STORAGE_ORIGIN}/tripdiary-photos/${s3Key}?X-Amz-Signature=x`,
        s3Key,
        expiresAt: '2026-11-11T03:20:42Z',
      })
    }),
    http.put(`${STORAGE_ORIGIN}/tripdiary-photos/*`, ({ request }) => {
      onPut(
        new URL(request.url).pathname.replace('/tripdiary-photos/', ''),
        request.headers.get('Content-Type'),
      )
      return new HttpResponse(null, { status: 200, headers: STORAGE_CORS_HEADERS })
    }),
  ]
}

/** 既定の振る舞い: API は正常、ログインしていない（リフレッシュトークンなし）、スポットは0件。 */
export const defaultHandlers = [
  http.get('/actuator/health', () => HttpResponse.json({ status: 'UP' })),
  http.get('/api/v1/areas', () => HttpResponse.json(areasFixture)),
  http.get('/api/v1/spots', () => HttpResponse.json({ items: [], nextCursor: null })),
  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json(errorResponse('INVALID_REFRESH_TOKEN', 'ログインの有効期限が切れました'), {
      status: 401,
    }),
  ),
]
