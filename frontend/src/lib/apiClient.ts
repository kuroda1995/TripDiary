/**
 * API クライアント（docs/rules.md 3章 / docs/features/README.md「共通仕様」）。
 *
 * - すべてのリクエストに X-Request-Id を付与し、エラー時にサーバーログと突き合わせられるようにする
 * - エラーレスポンス（{ code, message, details, requestId }）を ApiError に変換する
 * - JWT の付与・401 時のトークン更新は feature/auth で追加する
 */

export type FieldError = { field: string; message: string }

export type ErrorResponseBody = {
  code: string
  message: string
  details?: FieldError[]
  requestId?: string | null
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: FieldError[]
  readonly requestId: string | null

  constructor(status: number, body: ErrorResponseBody, fallbackRequestId: string | null) {
    super(body.message)
    this.name = 'ApiError'
    this.status = status
    this.code = body.code
    this.details = body.details ?? []
    this.requestId = body.requestId ?? fallbackRequestId
  }
}

export const NETWORK_ERROR_CODE = 'NETWORK_ERROR'
const UNKNOWN_ERROR_MESSAGE = '通信エラーが発生しました。時間をおいて再度お試しください'

function newRequestId(): string {
  return crypto.randomUUID()
}

function isErrorResponseBody(value: unknown): value is ErrorResponseBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ErrorResponseBody).code === 'string' &&
    typeof (value as ErrorResponseBody).message === 'string'
  )
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const requestId = newRequestId()
  const headers = new Headers(init.headers)
  headers.set('X-Request-Id', requestId)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, { code: NETWORK_ERROR_CODE, message: UNKNOWN_ERROR_MESSAGE }, requestId)
  }

  const responseRequestId = response.headers.get('X-Request-Id') ?? requestId

  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null)
    throw new ApiError(
      response.status,
      isErrorResponseBody(body) ? body : { code: `HTTP_${response.status}`, message: UNKNOWN_ERROR_MESSAGE },
      responseRequestId,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
