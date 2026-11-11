import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'
import { spotKeys } from '@/features/spots/api'
import { SpotList } from '@/features/spots/components/SpotList'
import { fetchTimeline } from '../api'

/** タイムライン（ログイン必須）。フォロー中ユーザーの公開投稿を新着順に表示する。 */
export function TimelinePage() {
  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">タイムライン</h1>
      <SpotList
        queryKey={spotKeys.timeline()}
        fetchPage={fetchTimeline}
        label="タイムライン"
        emptyMessage={<TimelineEmpty />}
      />
    </section>
  )
}

/** 空のときは新着一覧へ誘導する（おすすめユーザーの表示は将来検討。docs/features/06_follow.md 9章）。 */
function TimelineEmpty() {
  return (
    <div className="space-y-3 rounded-lg border border-dashed p-6 text-center">
      <p className="text-sm text-muted-foreground">
        フォロー中のユーザーの投稿がここに表示されます。
        <br />
        新着スポットから、気になる旅人を見つけてフォローしてみましょう。
      </p>
      <Link to="/" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
        新着スポットを見る
      </Link>
    </div>
  )
}
