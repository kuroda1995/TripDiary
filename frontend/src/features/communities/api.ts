import { useMutation, useQueryClient } from '@tanstack/react-query'
import { boardKeys, type BoardTopic, type BoardTopicPage, type TopicInput } from '@/features/board/api'
import type { Author } from '@/features/spots/types'
import { apiFetch } from '@/lib/apiClient'
import type { CommunityCategory } from './categories'

export const COMMUNITY_PAGE_SIZE = 20

/** 一覧用のコミュニティ（docs/features/12_communities.md 4章）。 */
export type CommunitySummary = {
  id: number
  name: string
  /** 説明文の先頭（最大120文字） */
  descriptionExcerpt: string
  categories: CommunityCategory[]
  owner: Author
  /** 参加メンバー数（オーナーを含む） */
  memberCount: number
  /** 閲覧者が参加しているか（未ログインは false） */
  joined: boolean
  createdAt: string
}

export type Community = {
  id: number
  name: string
  /** プレーンテキスト（改行を含む）。HTML として表示しないこと */
  description: string
  categories: CommunityCategory[]
  owner: Author
  memberCount: number
  topicCount: number
  joined: boolean
  createdAt: string
}

export type Member = { user: Author; owner: boolean; joinedAt: string }

export type CommunityPage = { items: CommunitySummary[]; nextCursor: string | null }
export type MemberPage = { items: Member[]; nextCursor: string | null }
export type MembershipResult = { communityId: number; joined: boolean; memberCount: number }

export type CommunitySort = 'newest' | 'popular'
export type CommunitySearchQuery = { q: string; category: CommunityCategory | null; sort: CommunitySort }

export type CommunityInput = { name: string; description: string; categories: CommunityCategory[] }

export const communityKeys = {
  all: ['communities'] as const,
  lists: () => [...communityKeys.all, 'list'] as const,
  list: (query: CommunitySearchQuery) => [...communityKeys.lists(), query] as const,
  detail: (id: number) => [...communityKeys.all, 'detail', id] as const,
  members: (id: number) => [...communityKeys.all, 'members', id] as const,
  topics: (id: number) => [...communityKeys.all, 'topics', id] as const,
}

export function fetchCommunities(query: CommunitySearchQuery, cursor: string | null): Promise<CommunityPage> {
  const params = new URLSearchParams({ limit: String(COMMUNITY_PAGE_SIZE), sort: query.sort })
  if (query.q) params.set('q', query.q)
  if (query.category) params.set('category', query.category)
  if (cursor) params.set('cursor', cursor)
  return apiFetch<CommunityPage>(`/api/v1/communities?${params}`)
}

export function fetchCommunity(id: number): Promise<Community> {
  return apiFetch<Community>(`/api/v1/communities/${id}`)
}

function withPaging(path: string, cursor: string | null): string {
  const params = new URLSearchParams({ limit: String(COMMUNITY_PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return `${path}?${params}`
}

export function fetchMembers(id: number, cursor: string | null): Promise<MemberPage> {
  return apiFetch<MemberPage>(withPaging(`/api/v1/communities/${id}/members`, cursor))
}

export function fetchCommunityTopics(id: number, cursor: string | null): Promise<BoardTopicPage> {
  return apiFetch<BoardTopicPage>(withPaging(`/api/v1/communities/${id}/topics`, cursor))
}

export function useCreateCommunity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CommunityInput) =>
      apiFetch<Community>('/api/v1/communities', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: (community) => {
      queryClient.setQueryData(communityKeys.detail(community.id), community)
      return queryClient.invalidateQueries({ queryKey: communityKeys.lists(), refetchType: 'none' })
    },
  })
}

export function useDeleteCommunity(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>(`/api/v1/communities/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: communityKeys.detail(id) })
      queryClient.removeQueries({ queryKey: communityKeys.members(id) })
      queryClient.removeQueries({ queryKey: communityKeys.topics(id) })
      return queryClient.invalidateQueries({ queryKey: communityKeys.lists(), refetchType: 'none' })
    },
  })
}

/** 参加する（true）・退出する（false）。どちらも冪等。 */
export function setMembership(id: number, join: boolean): Promise<MembershipResult> {
  return apiFetch<MembershipResult>(`/api/v1/communities/${id}/membership`, {
    method: join ? 'PUT' : 'DELETE',
  })
}

/** 参加・退出の後は、詳細（参加状態・人数）を結果で書き換え、メンバー一覧と一覧（人数・参加中）を取り直す。 */
export function useMembership(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (join: boolean) => setMembership(id, join),
    onSuccess: (result) => {
      queryClient.setQueryData<Community>(communityKeys.detail(id), (current) =>
        current ? { ...current, joined: result.joined, memberCount: result.memberCount } : current,
      )
      void queryClient.invalidateQueries({ queryKey: communityKeys.members(id) })
      void queryClient.invalidateQueries({ queryKey: communityKeys.lists(), refetchType: 'none' })
    },
  })
}

export function useCreateCommunityTopic(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: TopicInput) =>
      apiFetch<BoardTopic>(`/api/v1/communities/${id}/topics`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: (topic) => {
      queryClient.setQueryData(boardKeys.topic(topic.id), topic)
      void queryClient.invalidateQueries({ queryKey: communityKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: communityKeys.topics(id) })
    },
  })
}
