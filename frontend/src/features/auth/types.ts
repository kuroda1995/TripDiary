/** ログイン中のユーザー（GET /api/v1/users/me・認証 API のレスポンス）。 */
export type Me = {
  id: number
  email: string
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  /** ゲストアカウントか（パスワードを変更できない。docs/features/01_auth.md 4.8） */
  guest: boolean
}

/** ゲストのログイン情報（GET /api/v1/auth/guest。ログイン画面に表示する）。 */
export type GuestCredentials = {
  email: string
  password: string
}

export type SignupInput = {
  email: string
  username: string
  displayName: string
  password: string
}

export type LoginInput = {
  email: string
  password: string
}
