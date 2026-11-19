import { Star } from 'lucide-react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { searchUrlForTempleFeature } from '@/features/search/searchParams'
import { cn } from '@/lib/utils'
import {
  averageRating,
  MAX_RATING,
  RATING_ITEMS,
  TEMPLE_FEATURE_LABELS,
  type TempleFeature,
  type TempleRatings,
} from '../temple'

/** 星5つで点数を表す（読み上げは「5段階中4」）。 */
function Stars({ value, className }: { value: number; className?: string }) {
  return (
    <span role="img" aria-label={`5段階中${value}`} className={cn('inline-flex', className)}>
      {Array.from({ length: MAX_RATING }, (_, i) => (
        <Star
          key={i}
          aria-hidden
          className={cn(
            'size-4',
            i < Math.round(value) ? 'fill-amber-400 text-amber-500' : 'text-muted-foreground/40',
          )}
        />
      ))}
    </span>
  )
}

/** スポット詳細のお寺の評価（評価した項目だけ表示する）。 */
export function TempleRatingList({ ratings }: { ratings: TempleRatings | null }) {
  if (!ratings) return null
  const rated = RATING_ITEMS.filter(({ key }) => ratings[key] !== null)
  return (
    <section aria-labelledby="temple-ratings-heading" className="space-y-2 rounded-lg border p-4">
      <h2 id="temple-ratings-heading" className="font-semibold">
        お寺の評価
      </h2>
      <dl className="grid grid-cols-[max-content_1fr] items-center gap-x-6 gap-y-1.5 text-sm">
        {rated.map(({ key, label }) => (
          <div key={key} className="contents">
            <dt className="text-muted-foreground">{label}</dt>
            <dd className="flex items-center gap-2">
              <Stars value={ratings[key]!} />
              <span className="tabular-nums">{ratings[key]}</span>
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

/** 一覧カードの評価の平均（評価がなければ何も出さない）。 */
export function TempleRatingSummary({ ratings }: { ratings: TempleRatings | null }) {
  const average = averageRating(ratings)
  if (average === null) return null
  return (
    <p className="inline-flex items-center gap-1.5 text-xs">
      <span className="text-muted-foreground">お寺の評価</span>
      <Stars value={average} />
      <span className="font-medium tabular-nums">{average.toFixed(1)}</span>
    </p>
  )
}

/** お寺の特徴タグ。クリックするとそのタグで絞り込んだ検索結果を表示する。 */
export function TempleFeatureBadges({ features }: { features: TempleFeature[] }) {
  if (features.length === 0) return null
  return (
    <ul aria-label="お寺の特徴" className="flex flex-wrap gap-1.5">
      {features.map((feature) => (
        <li key={feature}>
          <Badge
            variant="outline"
            render={
              <Link
                to={searchUrlForTempleFeature(feature)}
                aria-label={`「${TEMPLE_FEATURE_LABELS[feature]}」のお寺を探す`}
              />
            }
          >
            {TEMPLE_FEATURE_LABELS[feature]}
          </Badge>
        </li>
      ))}
    </ul>
  )
}
