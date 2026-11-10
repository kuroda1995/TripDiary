import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '@/lib/apiClient'

export type HealthResponse = { status: 'UP' | 'DOWN' | string }

export function fetchHealth(): Promise<HealthResponse> {
  return apiFetch<HealthResponse>('/actuator/health')
}

/** バックエンド（API + DB）の稼働状況。 */
export function useHealth() {
  return useQuery({ queryKey: ['system', 'health'], queryFn: fetchHealth, retry: false })
}
