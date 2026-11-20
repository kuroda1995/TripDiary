import { Award } from 'lucide-react'
import type { CommunityBadge } from '@/features/spots/types'
import { cn } from '@/lib/utils'
import { BADGE_INFO } from '../badge'

const COLORS: Record<CommunityBadge, string> = {
  BEGINNER:
    'border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950 dark:text-emerald-200',
  PILGRIM: 'border-sky-300 bg-sky-50 text-sky-800 dark:border-sky-700 dark:bg-sky-950 dark:text-sky-200',
  MASTER:
    'border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200',
}

/** ユーザー名の横に出すコミュニティ称号（称号がなければ何も出さない）。 */
export function CommunityBadgeLabel({ badge }: { badge: CommunityBadge | null | undefined }) {
  if (!badge) return null
  const { label, threshold } = BADGE_INFO[badge]
  return (
    <span
      title={`投稿・コメント・掲示板の書き込みが${threshold}件以上`}
      className={cn(
        'inline-flex shrink-0 items-center gap-0.5 rounded-full border px-1.5 py-px text-[0.7rem] font-medium whitespace-nowrap',
        COLORS[badge],
      )}
    >
      <Award aria-hidden className="size-3" />
      {label}
    </span>
  )
}
