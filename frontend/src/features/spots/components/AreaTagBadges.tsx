import { Badge } from '@/components/ui/badge'
import type { AreaTag } from '@/features/areas/types'

export function AreaTagBadges({ tags }: { tags: AreaTag[] }) {
  if (tags.length === 0) return null
  return (
    <ul aria-label="エリアタグ" className="flex flex-wrap gap-1.5">
      {tags.map((tag) => (
        <li key={tag.id}>
          <Badge variant="secondary">{tag.name}</Badge>
        </li>
      ))}
    </ul>
  )
}
