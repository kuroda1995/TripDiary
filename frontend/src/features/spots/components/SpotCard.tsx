import { MessageCircle } from 'lucide-react'
import { motion, type Transition, type Variants } from 'motion/react'
import { Link } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { CommunityBadgeLabel } from '@/features/community/components/CommunityBadgeLabel'
import { HelpfulButton } from '@/features/helpful/components/HelpfulButton'
import { LikeButton } from '@/features/likes/components/LikeButton'
import { StatusSelect } from '@/features/status/components/StatusSelect'
import { formatDate } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { SpotSummary } from '../types'
import { AreaTagBadges } from './AreaTagBadges'
import { TempleFeatureBadges, TempleRatingSummary } from './TempleReview'

/** 一覧のスポットカード。 */
export function SpotCard({ spot }: { spot: SpotSummary }) {
  return (
    <MotionCard
      // ホバーでカードを少し浮かせ、カバー写真をわずかにズームする（タッチ端末では発火しない）
      initial="rest"
      whileHover="hover"
      variants={cardHover}
      transition={hoverSpring}
      className={cn('h-full transition-shadow hover:shadow-lg', spot.coverPhotoUrl && 'pt-0')}
    >
      {spot.coverPhotoUrl && (
        <div className="overflow-hidden rounded-t-xl">
          {/* タイトルが見出しリンクになっているため、カバー写真は装飾扱い（読み上げの重複を避ける） */}
          <motion.img
            src={spot.coverPhotoUrl}
            alt=""
            loading="lazy"
            variants={imageZoom}
            transition={hoverSpring}
            className="aspect-video w-full bg-muted object-cover"
          />
        </div>
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
        <TempleFeatureBadges features={spot.templeFeatures} />
        <TempleRatingSummary ratings={spot.ratings} />
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
          <CommunityBadgeLabel badge={spot.author.badge} />
          {spot.visitedOn && <span> ・ {formatDate(spot.visitedOn)}に訪問</span>}
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <LikeButton spotId={spot.id} liked={spot.likedByMe} likeCount={spot.likeCount} />
          <HelpfulButton spotId={spot.id} helpful={spot.helpfulByMe} helpfulCount={spot.helpfulCount} />
          <StatusSelect spotId={spot.id} status={spot.myStatus} />
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <MessageCircle aria-hidden className="size-4" />
            コメント {spot.commentCount}件
          </span>
        </div>
      </CardContent>
    </MotionCard>
  )
}

const MotionCard = motion.create(Card)

const cardHover: Variants = { rest: { y: 0 }, hover: { y: -4 } }
const imageZoom: Variants = { rest: { scale: 1 }, hover: { scale: 1.05 } }
const hoverSpring: Transition = { type: 'spring', stiffness: 300, damping: 24 }
