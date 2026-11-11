import type { AreaTag } from '@/features/areas/types'

export type Visibility = 'PUBLIC' | 'PRIVATE'

/** 自分用リストのステータス（docs/features/08_spot_status.md）。 */
export type SpotStatus = 'VISITED' | 'WANT_TO_GO'

export type Author = {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
}

export type SpotPhoto = {
  s3Key: string
  url: string
  sortOrder: number
}

/** スポット詳細（GET /api/v1/spots/{id}）。 */
export type Spot = {
  id: number
  title: string
  locationName: string
  areaTags: AreaTag[]
  impression: string | null
  visitedOn: string | null
  visibility: Visibility
  /** 表示順（先頭がカバー写真） */
  photos: SpotPhoto[]
  author: Author
  likeCount: number
  likedByMe: boolean
  commentCount: number
  /** 閲覧者が付けたステータス（未設定・未ログインは null。本人にだけ返る） */
  myStatus: SpotStatus | null
  createdAt: string
  updatedAt: string
}

/** 一覧用のスポット。 */
export type SpotSummary = {
  id: number
  title: string
  locationName: string
  areaTags: AreaTag[]
  impressionExcerpt: string | null
  visitedOn: string | null
  visibility: Visibility
  coverPhotoUrl: string | null
  author: Author
  likeCount: number
  likedByMe: boolean
  commentCount: number
  /** 閲覧者が付けたステータス（未設定・未ログインは null。本人にだけ返る） */
  myStatus: SpotStatus | null
  createdAt: string
}

export type SpotPage = {
  items: SpotSummary[]
  nextCursor: string | null
}

/** 作成・更新リクエスト。 */
export type SpotInput = {
  title: string
  locationName: string
  areaTagIds: number[]
  impression: string | null
  visitedOn: string | null
  visibility: Visibility
  photos: { s3Key: string; sortOrder: number }[]
}
