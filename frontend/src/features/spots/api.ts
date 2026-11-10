import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/lib/apiClient'
import type { Spot, SpotInput, SpotPage } from './types'

export const PAGE_SIZE = 20

export const spotKeys = {
  all: ['spots'] as const,
  latest: () => [...spotKeys.all, 'latest'] as const,
  byUser: (username: string) => [...spotKeys.all, 'user', username.toLowerCase()] as const,
  detail: (id: number) => [...spotKeys.all, 'detail', id] as const,
}

function withPaging(path: string, cursor: string | null): string {
  const params = new URLSearchParams({ limit: String(PAGE_SIZE) })
  if (cursor) params.set('cursor', cursor)
  return `${path}?${params}`
}

export function fetchLatestSpots(cursor: string | null): Promise<SpotPage> {
  return apiFetch<SpotPage>(withPaging('/api/v1/spots', cursor))
}

export function fetchUserSpots(username: string, cursor: string | null): Promise<SpotPage> {
  return apiFetch<SpotPage>(withPaging(`/api/v1/users/${encodeURIComponent(username)}/spots`, cursor))
}

export function fetchSpot(id: number): Promise<Spot> {
  return apiFetch<Spot>(`/api/v1/spots/${id}`)
}

/**
 * スポット詳細。本人の非公開スポットはログインしていないと 404 になるため、
 * ログイン状態の確認中（enabled = false）は取得を待つ。
 */
export function useSpot(id: number, enabled = true) {
  return useQuery({ queryKey: spotKeys.detail(id), queryFn: () => fetchSpot(id), enabled })
}

export function useCreateSpot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpotInput) =>
      apiFetch<Spot>('/api/v1/spots', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: (spot) => {
      queryClient.setQueryData(spotKeys.detail(spot.id), spot)
      return queryClient.invalidateQueries({ queryKey: spotKeys.all, refetchType: 'none' })
    },
  })
}

export function useUpdateSpot(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpotInput) =>
      apiFetch<Spot>(`/api/v1/spots/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    onSuccess: (spot) => {
      queryClient.setQueryData(spotKeys.detail(spot.id), spot)
      return queryClient.invalidateQueries({ queryKey: spotKeys.all, refetchType: 'none' })
    },
  })
}

export function useDeleteSpot(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>(`/api/v1/spots/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: spotKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: spotKeys.all, refetchType: 'none' })
    },
  })
}
