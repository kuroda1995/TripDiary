/**
 * ログイン後の遷移先を安全な値にする（オープンリダイレクト対策）。
 *
 * 同一サイト内のパス（"/" で始まり "//" や "/\" で始まらない）だけを許可し、それ以外はトップへ戻す。
 */
export function safeRedirectPath(value: string | null | undefined): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return '/'
  }
  return value
}

/** ログイン画面の URL（ログイン後に元の画面へ戻れるよう redirect を付ける）。 */
export function loginPathFor(currentPath: string): string {
  return `/login?redirect=${encodeURIComponent(currentPath)}`
}
