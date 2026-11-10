import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDate } from '@/lib/date'
import type { SpotSummary } from '../types'
import { AreaTagBadges } from './AreaTagBadges'

/** 一覧のスポットカード。カバー写真は写真アップロード機能で追加する。 */
export function SpotCard({ spot }: { spot: SpotSummary }) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">
            <Link to={`/spots/${spot.id}`} className="hover:underline">
              {spot.title}
            </Link>
          </h2>
        </CardTitle>
        <CardDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span>{spot.locationName}</span>
          {spot.visibility === 'PRIVATE' && <Badge variant="outline">非公開</Badge>}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">
        <AreaTagBadges tags={spot.areaTags} />
        {spot.impressionExcerpt && (
          <p className="whitespace-pre-line text-muted-foreground">{spot.impressionExcerpt}</p>
        )}
        <p className="text-xs text-muted-foreground">
          <Link to={`/users/${spot.author.username}`} className="hover:underline">
            {spot.author.displayName}
          </Link>
          {spot.visitedOn && <span> ・ {formatDate(spot.visitedOn)}に訪問</span>}
        </p>
      </CardContent>
    </Card>
  )
}
