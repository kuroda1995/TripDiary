import { Link } from 'react-router'
import { Badge } from '@/components/ui/badge'
import type { AreaTag } from '@/features/areas/types'
import { searchUrlForArea } from '@/features/search/searchParams'

/** スポットのエリアタグ。クリックするとそのタグで検索した結果を表示する。 */
export function AreaTagBadges({ tags }: { tags: AreaTag[] }) {
  if (tags.length === 0) return null
  return (
    <ul aria-label="エリアタグ" className="flex flex-wrap gap-1.5">
      {tags.map((tag) => (
        <li key={tag.id}>
          <Badge
            variant="secondary"
            render={<Link to={searchUrlForArea(tag.slug)} aria-label={`${tag.name}のスポットを探す`} />}
          >
            {tag.name}
          </Badge>
        </li>
      ))}
    </ul>
  )
}
