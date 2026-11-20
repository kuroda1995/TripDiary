import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Author } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'

export const BOARD_PAGE_SIZE = 20

/** 掲示板のトピック（一覧用。docs/features/10_community_board.md 4章）。 */
export type BoardTopicSummary = {
  id: number
  title: string
  /** 本文の先頭（最大120文字） */
  bodyExcerpt: string
  author: Author
  replyCount: number
  /** 最後に書き込みがあった日時（作成または最新の返信） */
  lastActivityAt: string
  createdAt: string
}

/** トピックが属するコミュニティ（docs/features/12_communities.md）。 */
export type TopicCommunity = { id: number; name: string; ownerId: number }

export type BoardTopic = {
  id: number
  title: string
  /** プレーンテキスト（改行を含む）。HTML として表示しないこと */
  body: string
  author: Author
  replyCount: number
  lastActivityAt: string
  createdAt: string
  /** コミュニティ内のトピックならそのコミュニティ（広場のトピックは null） */
  community: TopicCommunity | null
}

export type BoardReply = {
  id: number
  topicId: number
  /** プレーンテキスト（改行を含む）。HTML として表示しないこと */
  body: string
  author: Author
  createdAt: string
}

export type BoardTopicPage = { items: BoardTopicSummary[]; nextCursor: string | null }
export type BoardReplyPage = { items: BoardReply[]; nextCursor: string | null }

export type TopicInput = { title: string; body: string }

export const boardKeys = {
  all: ['board'] as const,
  topics: () => [...boardKeys.all, 'topics'] as const,
  topic: (id: number) => [...boardKeys.all, 'topic', id] as const,
  replies: (topicId: number) => [...boardKeys.all, 'replies', topicId] as const,
}

function withPaging(path: string, cursor: string | null): string {
  const params = new URLSearchParams({ limit: String(BOARD_PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return `${path}?${params}`
}

export function fetchTopics(cursor: string | null): Promise<BoardTopicPage> {
  return apiFetch<BoardTopicPage>(withPaging('/api/v1/board-topics', cursor))
}

export function fetchTopic(id: number): Promise<BoardTopic> {
  return apiFetch<BoardTopic>(`/api/v1/board-topics/${id}`)
}

export function fetchReplies(topicId: number, cursor: string | null): Promise<BoardReplyPage> {
  return apiFetch<BoardReplyPage>(withPaging(`/api/v1/board-topics/${topicId}/replies`, cursor))
}

export function useCreateTopic() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: TopicInput) =>
      apiFetch<BoardTopic>('/api/v1/board-topics', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: (topic) => {
      queryClient.setQueryData(boardKeys.topic(topic.id), topic)
      return queryClient.invalidateQueries({ queryKey: boardKeys.topics() })
    },
  })
}

/**
 * トピック一覧（広場とコミュニティの両方）を次に表示するときに取り直す。
 * コミュニティの一覧のキーは features/communities/api.ts の communityKeys.topics と同じ形（循環参照を避けて直接書く）。
 */
function invalidateTopicLists(queryClient: ReturnType<typeof useQueryClient>, communityId: number | null) {
  void queryClient.invalidateQueries({ queryKey: boardKeys.topics(), refetchType: 'none' })
  if (communityId !== null) {
    void queryClient.invalidateQueries({
      queryKey: ['communities', 'topics', communityId],
      refetchType: 'none',
    })
    void queryClient.invalidateQueries({
      queryKey: ['communities', 'detail', communityId],
      refetchType: 'none',
    })
  }
}

export function useDeleteTopic(topicId: number, communityId: number | null = null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>(`/api/v1/board-topics/${topicId}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: boardKeys.topic(topicId) })
      queryClient.removeQueries({ queryKey: boardKeys.replies(topicId) })
      invalidateTopicLists(queryClient, communityId)
    },
  })
}

/** 返信の投稿・削除の後は、返信一覧・トピック（返信数）・トピック一覧（並び順）を取り直す。 */
function useReplyMutation<TVariables, TResult>(
  topicId: number,
  communityId: number | null,
  mutationFn: (variables: TVariables) => Promise<TResult>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: boardKeys.replies(topicId) })
      await queryClient.invalidateQueries({ queryKey: boardKeys.topic(topicId) })
      invalidateTopicLists(queryClient, communityId)
    },
  })
}

export function usePostReply(topicId: number, communityId: number | null = null) {
  return useReplyMutation(topicId, communityId, (body: string) =>
    apiFetch<BoardReply>(`/api/v1/board-topics/${topicId}/replies`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),
  )
}

export function useDeleteReply(topicId: number, communityId: number | null = null) {
  return useReplyMutation(topicId, communityId, (replyId: number) =>
    apiFetch<void>(`/api/v1/board-replies/${replyId}`, { method: 'DELETE' }),
  )
}
