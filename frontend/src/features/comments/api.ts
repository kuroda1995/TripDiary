import { useMutation, useQueryClient } from '@tanstack/react-query'
import { spotKeys } from '@/features/spots/api'
import type { Author } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'

export const COMMENT_PAGE_SIZE = 20

/** コメント（docs/features/05_comment.md 4.1）。 */
export type Comment = {
  id: number
  spotId: number
  /** プレーンテキスト（改行を含む）。HTML として表示しないこと */
  body: string
  author: Author
  edited: boolean
  createdAt: string
  updatedAt: string
}

export type CommentPage = { items: Comment[]; nextCursor: string | null }

export const commentKeys = {
  list: (spotId: number) => ['comments', spotId] as const,
}

export function fetchComments(spotId: number, cursor: string | null): Promise<CommentPage> {
  const params = new URLSearchParams({ limit: String(COMMENT_PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return apiFetch<CommentPage>(`/api/v1/spots/${spotId}/comments?${params}`)
}

/**
 * コメントの投稿・編集・削除の共通処理。
 * 成功したらコメント一覧を取り直し、スポットのコメント件数（詳細・一覧）も古い扱いにする。
 */
function useCommentMutation<TVariables, TResult>(
  spotId: number,
  mutationFn: (variables: TVariables) => Promise<TResult>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: commentKeys.list(spotId) })
      await queryClient.invalidateQueries({ queryKey: spotKeys.detail(spotId) })
      void queryClient.invalidateQueries({ queryKey: spotKeys.all, refetchType: 'none' })
    },
  })
}

export function usePostComment(spotId: number) {
  return useCommentMutation(spotId, (body: string) =>
    apiFetch<Comment>(`/api/v1/spots/${spotId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),
  )
}

export function useEditComment(spotId: number) {
  return useCommentMutation(spotId, ({ commentId, body }: { commentId: number; body: string }) =>
    apiFetch<Comment>(`/api/v1/comments/${commentId}`, { method: 'PATCH', body: JSON.stringify({ body }) }),
  )
}

export function useDeleteComment(spotId: number) {
  return useCommentMutation(spotId, (commentId: number) =>
    apiFetch<void>(`/api/v1/comments/${commentId}`, { method: 'DELETE' }),
  )
}
