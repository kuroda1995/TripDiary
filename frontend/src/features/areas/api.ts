import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/lib/apiClient'
import type { AreaTagNode } from './types'

export function fetchAreas(): Promise<AreaTagNode[]> {
  return apiFetch<AreaTagNode[]>('/api/v1/areas')
}

/** エリアタグ一覧。ほとんど変わらないマスタデータなので、一度取得したら再取得しない。 */
export function useAreas() {
  return useQuery({ queryKey: ['areas'], queryFn: fetchAreas, staleTime: Infinity })
}
