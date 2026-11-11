/** プロフィール（GET /api/v1/users/{username}）。 */
export type Profile = {
  id: number
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  /** 投稿数（本人が見る場合は非公開も含む） */
  spotCount: number
  followingCount: number
  followerCount: number
  /** 自分がフォローしているか（未ログイン・本人は false） */
  followedByMe: boolean
}

/** PUT / DELETE /api/v1/users/{username}/follow のレスポンス。 */
export type FollowResult = { username: string; following: boolean; followerCount: number }

/** フォロー中一覧・フォロワー一覧の1行。 */
export type FollowUser = {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
  followedByMe: boolean
}

export type FollowUserPage = { items: FollowUser[]; nextCursor: string | null }

export type FollowListKind = 'following' | 'followers'
