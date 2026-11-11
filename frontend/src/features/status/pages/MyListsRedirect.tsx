import { Navigate, useSearchParams } from 'react-router'

/**
 * 仕様書のマイリストのパス（`/me/lists?status=want_to_go`）。マイページのタブに統合したため、該当するタブへ移動する。
 */
export function MyListsRedirect() {
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('status') === 'visited' ? '?tab=visited' : ''
  return <Navigate to={`/me${tab}`} replace />
}
