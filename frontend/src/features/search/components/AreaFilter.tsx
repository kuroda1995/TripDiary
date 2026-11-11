import { X } from 'lucide-react'
import type { AreaTagNode } from '@/features/areas/types'
import { MAX_AREAS } from '../searchParams'

type AreaFilterProps = {
  regions: AreaTagNode[]
  /** 選択中のスラッグ（地方・都道府県） */
  value: string[]
  onChange: (slugs: string[]) => void
}

/**
 * 検索用のエリア選択（docs/features/07_area_search.md 3章）。
 *
 * - 地方ごとに折りたたみ、「〇〇全体」を選ぶとその地方全体を選択する（その地方の個別の選択はまとめる）
 * - 選択中のタグはチップで表示し、× で外せる
 */
export function AreaFilter({ regions, value, onChange }: AreaFilterProps) {
  const nameBySlug = new Map<string, string>()
  regions.forEach((region) => {
    nameBySlug.set(region.slug, `${region.name}全体`)
    region.children.forEach((tag) => nameBySlug.set(tag.slug, tag.name))
  })
  const selected = new Set(value)
  const full = value.length >= MAX_AREAS

  const remove = (slug: string) => onChange(value.filter((s) => s !== slug))
  const add = (slug: string) => onChange([...value, slug])
  const toggleRegion = (region: AreaTagNode) => {
    if (selected.has(region.slug)) {
      remove(region.slug)
      return
    }
    const childSlugs = new Set(region.children.map((tag) => tag.slug))
    onChange([...value.filter((s) => !childSlugs.has(s)), region.slug])
  }

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">エリア（{MAX_AREAS}個まで・いずれかに当てはまる投稿）</legend>

      {value.length > 0 && (
        <ul aria-label="選択中のエリア" className="flex flex-wrap gap-1.5">
          {value.map((slug) => (
            <li key={slug}>
              <button
                type="button"
                onClick={() => remove(slug)}
                className="inline-flex items-center gap-1 rounded-full bg-primary px-2.5 py-0.5 text-xs text-primary-foreground hover:bg-primary/80"
                aria-label={`${nameBySlug.get(slug) ?? slug} を外す`}
              >
                {nameBySlug.get(slug) ?? slug}
                <X className="size-3" aria-hidden />
              </button>
            </li>
          ))}
          <li>
            <button
              type="button"
              onClick={() => onChange([])}
              className="px-1 text-xs text-muted-foreground underline-offset-2 hover:underline"
            >
              すべて外す
            </button>
          </li>
        </ul>
      )}

      <div className="divide-y rounded-lg border">
        {regions.map((region) => {
          const regionSelected = selected.has(region.slug)
          const selectedInRegion = region.children.filter((tag) => selected.has(tag.slug)).length
          return (
            <details key={region.id} className="px-3 py-2">
              <summary className="cursor-pointer text-sm select-none">
                {region.name}
                {(regionSelected || selectedInRegion > 0) && (
                  <span className="ml-2 text-xs text-muted-foreground">
                    （{regionSelected ? '全体を選択中' : `${selectedInRegion}件選択中`}）
                  </span>
                )}
              </summary>
              <div className="mt-2 space-y-2">
                <label className="flex items-center gap-2 text-sm font-medium">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={regionSelected}
                    disabled={!regionSelected && full && selectedInRegion === 0}
                    onChange={() => toggleRegion(region)}
                  />
                  {region.name}全体
                </label>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 sm:grid-cols-4">
                  {region.children.map((tag) => {
                    // 地方全体を選んでいるときは、含まれていることを示すためチェック済み・変更不可にする
                    const checked = regionSelected || selected.has(tag.slug)
                    const disabled = regionSelected || (!checked && full)
                    return (
                      <label
                        key={tag.id}
                        className={`flex items-center gap-2 text-sm ${disabled ? 'text-muted-foreground' : ''}`}
                      >
                        <input
                          type="checkbox"
                          className="size-4 accent-primary"
                          checked={checked}
                          disabled={disabled}
                          onChange={() => (selected.has(tag.slug) ? remove(tag.slug) : add(tag.slug))}
                        />
                        {tag.name}
                      </label>
                    )
                  })}
                </div>
              </div>
            </details>
          )
        })}
      </div>
    </fieldset>
  )
}
