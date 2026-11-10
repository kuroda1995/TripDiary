import { useParams } from 'react-router'
import { useAuth } from '@/features/auth/useAuth'
import { ApiError } from '@/lib/apiClient'
import { fetchUserSpots, spotKeys } from '../api'
import { SpotList } from '../components/SpotList'

/** ユーザーのプロフィール（投稿一覧）。フォロー・プロフィール情報はフォロー機能で追加する。 */
export function UserSpotsPage() {
  const { username = '' } = useParams()
  const auth = useAuth()
  const isMe = auth.status === 'authenticated' && auth.user.username.toLowerCase() === username.toLowerCase()

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">@{username} の投稿</h1>
      {isMe && (
        <p className="text-sm text-muted-foreground">非公開のスポットはあなたにだけ表示されています。</p>
      )}
      <SpotList
        queryKey={spotKeys.byUser(username)}
        fetchPage={(cursor) => fetchUserSpots(username, cursor)}
        // 本人なら非公開も含むため、ログイン状態の確認が終わってから取得する
        enabled={auth.status !== 'loading'}
        emptyMessage="まだ投稿がありません。"
        label={`@${username} の投稿`}
        renderError={(error) =>
          error instanceof ApiError && error.status === 404 ? (
            <p className="text-muted-foreground">@{username} というユーザーは存在しません。</p>
          ) : null
        }
      />
    </section>
  )
}
