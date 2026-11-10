/**
 * API クライアント（docs/rules.md 3章 / docs/features/README.md「共通仕様」）。
 *
 * - すべてのリクエストに X-Request-Id を付与し、エラー時にサーバーログと突き合わせられるようにする
 * - ログイン中はアクセストークンを Authorization ヘッダに付ける
 * - アクセストークンの期限切れ（401 TOKEN_EXPIRED）は、リフレッシュトークン（HttpOnly Cookie）で更新して1回だけリトライする
 * - エラーレスポンス（{ code, message, details, requestId }）を ApiError に変換する
 */
import { getAccessToken, notifySessionExpired, setAccessToken } from './authSession'

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

/** トークン更新の API。この API 自体はリトライ・トークン付与の対象外。 */
export const REFRESH_PATH = '/api/v1/auth/refresh'
const AUTH_PATH_PREFIX = '/api/v1/auth/'

export type AuthenticatedResponse<TUser> = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: TUser
}

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

async function send<T>(path: string, init: RequestInit): Promise<T> {
  const requestId = newRequestId()
  const headers = new Headers(init.headers)
  headers.set('X-Request-Id', requestId)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getAccessToken()
  if (token && !path.startsWith(AUTH_PATH_PREFIX)) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let response: Response
  try {
    // same-origin: リフレッシュトークン Cookie を認証 API に送るため
    response = await fetch(path, { credentials: 'same-origin', ...init, headers })
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

let refreshInFlight: Promise<AuthenticatedResponse<unknown>> | null = null

/**
 * リフレッシュトークン（Cookie）でアクセストークンを更新する。
 *
 * 同時に複数回呼ばれても通信は1回にまとめる（リフレッシュトークンは使い捨てで、二重に使うと全トークンが失効するため）。
 */
export function refreshAccessToken<TUser>(): Promise<AuthenticatedResponse<TUser>> {
  if (!refreshInFlight) {
    refreshInFlight = send<AuthenticatedResponse<unknown>>(REFRESH_PATH, { method: 'POST' })
      .then((result) => {
        setAccessToken(result.accessToken)
        return result
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight as Promise<AuthenticatedResponse<TUser>>
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  try {
    return await send<T>(path, init)
  } catch (error) {
    const canRetry =
      error instanceof ApiError &&
      error.status === 401 &&
      error.code === 'TOKEN_EXPIRED' &&
      !path.startsWith(AUTH_PATH_PREFIX)
    if (!canRetry) {
      throw error
    }
    try {
      await refreshAccessToken()
    } catch {
      notifySessionExpired()
      throw error
    }
    return send<T>(path, init)
  }
}
