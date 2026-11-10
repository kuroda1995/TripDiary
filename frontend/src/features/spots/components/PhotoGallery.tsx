import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import type { SpotPhoto } from '../types'

/** スポット詳細の写真カルーセル（大きな写真 + 前後ボタン + サムネイル）。 */
export function PhotoGallery({ photos, title }: { photos: SpotPhoto[]; title: string }) {
  const [current, setCurrent] = useState(0)
  if (photos.length === 0) return null

  const index = Math.min(current, photos.length - 1)
  const photo = photos[index]
  const hasMany = photos.length > 1

  return (
    <section aria-label="写真" className="space-y-2">
      <div className="relative overflow-hidden rounded-lg bg-muted">
        <img
          src={photo.url}
          alt={`${title}の写真（${index + 1}/${photos.length}枚目）`}
          className="aspect-[4/3] w-full object-contain"
        />
        {hasMany && (
          <>
            <Button
              type="button"
              size="icon"
              variant="secondary"
              aria-label="前の写真"
              className="absolute top-1/2 left-2 -translate-y-1/2"
              onClick={() => setCurrent((index - 1 + photos.length) % photos.length)}
            >
              <ChevronLeft aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon"
              variant="secondary"
              aria-label="次の写真"
              className="absolute top-1/2 right-2 -translate-y-1/2"
              onClick={() => setCurrent((index + 1) % photos.length)}
            >
              <ChevronRight aria-hidden />
            </Button>
          </>
        )}
      </div>
      {hasMany && (
        <ul className="flex gap-2 overflow-x-auto pb-1">
          {photos.map((thumb, i) => (
            <li key={thumb.s3Key} className="shrink-0">
              <button
                type="button"
                aria-label={`${i + 1}枚目の写真を表示`}
                aria-current={i === index ? 'true' : undefined}
                onClick={() => setCurrent(i)}
                className={`block overflow-hidden rounded-md border-2 ${i === index ? 'border-primary' : 'border-transparent opacity-70 hover:opacity-100'}`}
              >
                <img src={thumb.url} alt="" loading="lazy" className="size-16 object-cover" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
