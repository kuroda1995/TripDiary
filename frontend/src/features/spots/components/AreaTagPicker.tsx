import { useId } from 'react'
import { X } from 'lucide-react'
import { useAreas } from '@/features/areas/api'
import type { AreaTagNode } from '@/features/areas/types'

type AreaTagPickerProps = {
  value: number[]
  onChange: (ids: number[]) => void
  max: number
  error?: string
}

/**
 * エリアタグの選択（地方ごとに折りたたみ、都道府県・国や地域を複数選択）。
 * 上限に達したら未選択のタグは選べなくする。
 */
export function AreaTagPicker({ value, onChange, max, error }: AreaTagPickerProps) {
  const { data: regions, isPending, isError } = useAreas()
  const labelId = useId()
  const errorId = useId()

  const tagsById = new Map<number, AreaTagNode>()
  regions?.forEach((region) => region.children.forEach((tag) => tagsById.set(tag.id, tag)))

  const toggle = (id: number) => onChange(value.includes(id) ? value.filter((v) => v !== id) : [...value, id])

  return (
    <fieldset
      className="space-y-2"
      aria-labelledby={labelId}
      aria-describedby={error ? errorId : undefined}
      aria-invalid={error ? true : undefined}
    >
      <legend id={labelId} className="text-sm font-medium">
        エリアタグ（{max}個まで）
      </legend>

      {value.length > 0 && (
        <ul aria-label="選択中のエリアタグ" className="flex flex-wrap gap-1.5">
          {value.map((id) => (
            <li key={id}>
              <button
                type="button"
                onClick={() => toggle(id)}
                className="inline-flex items-center gap-1 rounded-full bg-primary px-2.5 py-0.5 text-xs text-primary-foreground hover:bg-primary/80"
                aria-label={`${tagsById.get(id)?.name ?? id} を外す`}
              >
                {tagsById.get(id)?.name ?? '…'}
                <X className="size-3" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
      )}

      {isPending && <p className="text-sm text-muted-foreground">エリアタグを読み込み中…</p>}
      {isError && <p className="text-sm text-destructive">エリアタグを読み込めませんでした。</p>}

      <div className="divide-y rounded-lg border">
        {regions?.map((region) => {
          const selectedInRegion = region.children.filter((tag) => value.includes(tag.id)).length
          return (
            <details key={region.id} className="group px-3 py-2">
              <summary className="cursor-pointer text-sm select-none">
                {region.name}
                {selectedInRegion > 0 && (
                  <span className="ml-2 text-xs text-muted-foreground">（{selectedInRegion}件選択中）</span>
                )}
              </summary>
              <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1.5 sm:grid-cols-4">
                {region.children.map((tag) => {
                  const checked = value.includes(tag.id)
                  const disabled = !checked && value.length >= max
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
                        onChange={() => toggle(tag.id)}
                      />
                      {tag.name}
                    </label>
                  )
                })}
              </div>
            </details>
          )
        })}
      </div>

      {error && (
        <p id={errorId} className="text-xs text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  )
}
