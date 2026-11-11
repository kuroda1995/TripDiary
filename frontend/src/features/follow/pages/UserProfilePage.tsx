import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/features/auth/useAuth'
import { fetchUserSpots, spotKeys } from '@/features/spots/api'
import { SpotList } from '@/features/spots/components/SpotList'
import { ApiError } from '@/lib/apiClient'
import { fetchProfile, userKeys } from '../api'
import { FollowButton } from '../components/FollowButton'
import type { Profile } from '../types'

/** ユーザーのプロフィール（docs/features/06_follow.md 3章）。プロフィール・件数・フォローボタンと投稿一覧。 */
export function UserProfilePage() {
  const { username = '' } = useParams()
  const auth = useAuth()
  const isMe = auth.status === 'authenticated' && auth.user.username.toLowerCase() === username.toLowerCase()

  // 投稿数・followedByMe はログイン状態で変わるため、確認が終わってから取得する
  const profile = useQuery({
    queryKey: userKeys.profile(username),
    queryFn: () => fetchProfile(username),
    enabled: auth.status !== 'loading',
  })

  if (profile.isError) {
    if (profile.error instanceof ApiError && profile.error.status === 404) {
      return <p className="text-muted-foreground">@{username} というユーザーは存在しません。</p>
    }
    return <p className="text-sm text-destructive">プロフィールを読み込めませんでした。</p>
  }
  if (!profile.data) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }

  const user = profile.data
  return (
    <div className="space-y-8">
      <ProfileHeader profile={user} isMe={isMe} />
      <section aria-labelledby="user-spots-heading" className="space-y-4">
        <h2 id="user-spots-heading" className="text-xl font-bold">
          投稿
        </h2>
        {isMe && (
          <p className="text-sm text-muted-foreground">非公開のスポットはあなたにだけ表示されています。</p>
        )}
        <SpotList
          queryKey={spotKeys.byUser(username)}
          fetchPage={(cursor) => fetchUserSpots(username, cursor)}
          emptyMessage="まだ投稿がありません。"
          label={`@${user.username} の投稿`}
        />
      </section>
    </div>
  )
}

function ProfileHeader({ profile, isMe }: { profile: Profile; isMe: boolean }) {
  const countLink = 'hover:underline'
  return (
    <section aria-labelledby="profile-heading" className="space-y-4">
      <div className="flex flex-wrap items-center gap-4">
        <UserAvatar displayName={profile.displayName} avatarUrl={profile.avatarUrl} size="lg" />
        <div className="min-w-0 flex-1">
          <h1 id="profile-heading" className="text-2xl font-bold break-words">
            {profile.displayName}
          </h1>
          <p className="text-sm text-muted-foreground">@{profile.username}</p>
        </div>
        {isMe ? (
          <Link to="/settings/profile" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            プロフィール編集
          </Link>
        ) : (
          <FollowButton username={profile.username} following={profile.followedByMe} />
        )}
      </div>
      {profile.bio && <p className="text-sm break-words whitespace-pre-wrap">{profile.bio}</p>}
      <ul aria-label="件数" className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
        <li>
          <Count value={profile.spotCount} label="投稿" />
        </li>
        <li>
          <Link to={`/users/${profile.username}/following`} className={countLink}>
            <Count value={profile.followingCount} label="フォロー中" />
          </Link>
        </li>
        <li>
          <Link to={`/users/${profile.username}/followers`} className={countLink}>
            <Count value={profile.followerCount} label="フォロワー" />
          </Link>
        </li>
      </ul>
    </section>
  )
}

function Count({ value, label }: { value: number; label: string }) {
  return (
    <>
      <span className="font-semibold tabular-nums">{value.toLocaleString('ja-JP')}</span>{' '}
      <span className="text-muted-foreground">{label}</span>
    </>
  )
}
