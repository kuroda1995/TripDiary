import { Link, useNavigate } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { CommentSection } from '@/features/comments/components/CommentSection'
import { LikeButton } from '@/features/likes/components/LikeButton'
import { ApiError } from '@/lib/apiClient'
import { formatDate, formatDateTime } from '@/lib/date'
import { useSpot } from '../api'
import { AreaTagBadges } from '../components/AreaTagBadges'
import { DeleteSpotButton } from '../components/DeleteSpotButton'
import { PhotoGallery } from '../components/PhotoGallery'
import { SpotNotFound } from '../components/SpotNotFound'
import { useSpotIdParam } from '../useSpotIdParam'

export function SpotDetailPage() {
  const spotId = useSpotIdParam()
  if (spotId === null) return <SpotNotFound />
  return <SpotDetail spotId={spotId} />
}

function SpotDetail({ spotId }: { spotId: number }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const { data: spot, isPending, error } = useSpot(spotId, auth.status !== 'loading')

  if (isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (error) {
    return error instanceof ApiError && error.status === 404 ? (
      <SpotNotFound />
    ) : (
      <p className="text-sm text-destructive">スポットを読み込めませんでした。</p>
    )
  }

  const isOwner = auth.status === 'authenticated' && auth.user.id === spot.author.id

  return (
    <article className="space-y-6">
      <header className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-2xl font-bold">{spot.title}</h1>
          {isOwner && (
            <div className="flex gap-2">
              <Link
                to={`/spots/${spot.id}/edit`}
                className={buttonVariants({ variant: 'outline', size: 'sm' })}
              >
                編集
              </Link>
              <DeleteSpotButton
                spotId={spot.id}
                spotTitle={spot.title}
                onDeleted={() => navigate(`/users/${spot.author.username}`, { replace: true })}
              />
            </div>
          )}
        </div>
        <p className="flex flex-wrap items-center gap-2 text-muted-foreground">
          <span>{spot.locationName}</span>
          {spot.visibility === 'PRIVATE' && <Badge variant="outline">非公開</Badge>}
        </p>
        <AreaTagBadges tags={spot.areaTags} />
        <LikeButton spotId={spot.id} liked={spot.likedByMe} likeCount={spot.likeCount} size="default" />
      </header>

      <PhotoGallery photos={spot.photos} title={spot.title} />

      {spot.impression ? (
        <p className="leading-relaxed whitespace-pre-wrap">{spot.impression}</p>
      ) : (
        <p className="text-sm text-muted-foreground">感想はまだありません。</p>
      )}

      <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 border-t pt-4 text-sm">
        <dt className="text-muted-foreground">投稿者</dt>
        <dd>
          <Link
            to={`/users/${spot.author.username}`}
            className="inline-flex items-center gap-1.5 hover:underline"
          >
            <UserAvatar displayName={spot.author.displayName} avatarUrl={spot.author.avatarUrl} size="sm" />
            {spot.author.displayName}
          </Link>
        </dd>
        {spot.visitedOn && (
          <>
            <dt className="text-muted-foreground">訪問日</dt>
            <dd>{formatDate(spot.visitedOn)}</dd>
          </>
        )}
        <dt className="text-muted-foreground">投稿日時</dt>
        <dd>
          <time dateTime={spot.createdAt}>{formatDateTime(spot.createdAt)}</time>
        </dd>
      </dl>

      <CommentSection spotId={spot.id} spotOwnerId={spot.author.id} commentCount={spot.commentCount} />
    </article>
  )
}
