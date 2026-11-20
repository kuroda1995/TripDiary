import { Badge } from '@/components/ui/badge'
import { COMMUNITY_CATEGORY_LABELS, type CommunityCategory } from '../categories'

/** カテゴリタグの一覧。 */
export function CategoryBadges({ categories }: { categories: CommunityCategory[] }) {
  if (categories.length === 0) return null
  return (
    <ul aria-label="カテゴリ" className="flex flex-wrap gap-1.5">
      {categories.map((category) => (
        <li key={category}>
          <Badge variant="secondary">{COMMUNITY_CATEGORY_LABELS[category]}</Badge>
        </li>
      ))}
    </ul>
  )
}
