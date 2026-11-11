import { MessageCircle } from 'lucide-react'
import { Link } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { LikeButton } from '@/features/likes/components/LikeButton'
import { StatusSelect } from '@/features/status/components/StatusSelect'
import { formatDate } from '@/lib/date'
import type { SpotSummary } from '../types'
import { AreaTagBadges } from './AreaTagBadges'

/** 一覧のスポットカード。 */
export function SpotCard({ spot }: { spot: SpotSummary }) {
  return (
    <Card className="h-full">
      {spot.coverPhotoUrl && (
        // タイトルが見出しリンクになっているため、カバー写真は装飾扱い（読み上げの重複を避ける）
        <img
          src={spot.coverPhotoUrl}
          alt=""
          loading="lazy"
          className="aspect-video w-full bg-muted object-cover"
        />
      )}
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
        <p className="flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
          <Link
            to={`/users/${spot.author.username}`}
            className="inline-flex items-center gap-1.5 hover:underline"
          >
            <UserAvatar displayName={spot.author.displayName} avatarUrl={spot.author.avatarUrl} size="sm" />
            {spot.author.displayName}
          </Link>
          {spot.visitedOn && <span> ・ {formatDate(spot.visitedOn)}に訪問</span>}
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <LikeButton spotId={spot.id} liked={spot.likedByMe} likeCount={spot.likeCount} />
          <StatusSelect spotId={spot.id} status={spot.myStatus} />
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <MessageCircle aria-hidden className="size-4" />
            コメント {spot.commentCount}件
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
