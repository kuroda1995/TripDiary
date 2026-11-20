import { Link } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import type { Author } from '@/features/spots/types'

/** 投稿者（アイコンと表示名。プロフィールへのリンク）。 */
export function AuthorLink({ author }: { author: Author }) {
  return (
    <Link
      to={`/users/${author.username}`}
      className="inline-flex items-center gap-1.5 font-medium hover:underline"
    >
      <UserAvatar displayName={author.displayName} avatarUrl={author.avatarUrl} size="sm" />
      {author.displayName}
    </Link>
  )
}
