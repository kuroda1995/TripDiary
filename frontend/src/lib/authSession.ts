/**
 * アクセストークンの保持（メモリのみ）。
 *
 * - XSS で盗まれないよう localStorage には保存しない（リロード時は HttpOnly Cookie のリフレッシュトークンで復元する）
 * - トークン更新に失敗した（＝ログインが切れた）ことを購読者に通知する
 */

let accessToken: string | null = null
const sessionExpiredListeners = new Set<() => void>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** ログイン切れの通知を購読する。戻り値の関数で購読を解除する。 */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener)
  return () => {
    sessionExpiredListeners.delete(listener)
  }
}

export function notifySessionExpired(): void {
  accessToken = null
  sessionExpiredListeners.forEach((listener) => listener())
}
