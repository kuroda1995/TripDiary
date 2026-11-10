/** ログイン中のユーザー（GET /api/v1/users/me・認証 API のレスポンス）。 */
export type Me = {
  id: number
  email: string
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
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
