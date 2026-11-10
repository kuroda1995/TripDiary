import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { fetchLatestSpots, spotKeys } from '@/features/spots/api'
import { SpotList } from '@/features/spots/components/SpotList'

export function HomePage() {
  const auth = useAuth()

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h1 className="text-3xl font-bold tracking-tight">旅の思い出を、みんなと。</h1>
        <p className="text-muted-foreground">
          TripDiary は、訪れたスポットを写真と感想で記録して共有できる旅行記録アプリです。
        </p>
        {auth.status === 'anonymous' && (
          <Link to="/signup" className={buttonVariants()}>
            はじめる（無料）
          </Link>
        )}
      </section>
      <section className="space-y-4">
        <h2 className="text-xl font-semibold">新着スポット</h2>
        <SpotList
          queryKey={spotKeys.latest()}
          fetchPage={fetchLatestSpots}
          emptyMessage="まだ投稿はありません。最初のスポットを投稿してみましょう。"
          label="新着スポット"
        />
      </section>
    </div>
  )
}
