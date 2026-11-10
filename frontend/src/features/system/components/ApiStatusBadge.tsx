import { Badge } from '@/components/ui/badge'
import { useHealth } from '../api'

/** バックエンドとの接続状況を表示する（開発中の動作確認・E2E のスモークテスト用）。 */
export function ApiStatusBadge() {
  const { data, isPending, isError } = useHealth()

  if (isPending) {
    return (
      <Badge variant="outline" data-testid="api-status">
        API: 確認中…
      </Badge>
    )
  }
  if (isError || data.status !== 'UP') {
    return (
      <Badge variant="destructive" data-testid="api-status">
        API: 接続できません
      </Badge>
    )
  }
  return (
    <Badge variant="secondary" data-testid="api-status">
      API: 正常
    </Badge>
  )
}
