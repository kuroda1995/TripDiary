import { Link } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { CommunityBadgeLabel } from '@/features/community/components/CommunityBadgeLabel'
import type { Author } from '@/features/spots/types'

/** 投稿者（アイコンと表示名。プロフィールへのリンク）と称号。 */
export function AuthorLink({ author }: { author: Author }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Link
        to={`/users/${author.username}`}
        className="inline-flex items-center gap-1.5 font-medium hover:underline"
      >
        <UserAvatar displayName={author.displayName} avatarUrl={author.avatarUrl} size="sm" />
        {author.displayName}
      </Link>
      <CommunityBadgeLabel badge={author.badge} />
    </span>
  )
}
