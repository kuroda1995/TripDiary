import { Search } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useAreas } from '@/features/areas/api'
import { spotKeys } from '@/features/spots/api'
import { SpotList } from '@/features/spots/components/SpotList'
import { fetchSpotSearch, type SpotSearchQuery } from '../api'
import { AreaFilter } from '../components/AreaFilter'
import {
  indexAreasBySlug,
  MAX_KEYWORD_LENGTH,
  parseSearchParams,
  toSearchParams,
  type SearchCondition,
  type SpotSort,
} from '../searchParams'

const SORT_LABELS: Record<SpotSort, string> = { newest: '新着順', popular: '行ってみたい！が多い順' }

/**
 * 検索画面（docs/features/07_area_search.md 3章）。
 *
 * 条件は URL のクエリ（`?areas=kyoto,osaka&q=温泉&sort=popular`）だけに持ち、共有・ブラウザの戻る操作に対応する。
 * エリア・並び順は変えた時点で、キーワードは「検索」で反映する。
 */
export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const areas = useAreas()
  const sortId = useId()

  const parsed = parseSearchParams(searchParams)
  const bySlug = areas.data ? indexAreasBySlug(areas.data) : null
  // 存在しないスラッグ（古い URL など）は無視する
  const condition: SearchCondition = bySlug
    ? { ...parsed, areas: parsed.areas.filter((slug) => bySlug.has(slug)) }
    : parsed

  const update = (patch: Partial<SearchCondition>) =>
    setSearchParams(toSearchParams({ ...condition, ...patch }))

  const query: SpotSearchQuery | null = bySlug
    ? {
        areaIds: condition.areas.map((slug) => bySlug.get(slug)!.id),
        q: condition.q.trim(),
        sort: condition.sort,
      }
    : null

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">スポットを探す</h1>

      <div className="space-y-4 rounded-lg border p-4">
        {/* URL のキーワードが変わったら（戻る操作など）入力欄も合わせる */}
        <KeywordForm key={condition.q} initial={condition.q} onSubmit={(q) => update({ q })} />
        {areas.isError && <p className="text-sm text-destructive">エリアタグを読み込めませんでした。</p>}
        {areas.data && (
          <AreaFilter
            regions={areas.data}
            value={condition.areas}
            onChange={(slugs) => update({ areas: slugs })}
          />
        )}
        <div className="flex items-center gap-2 text-sm">
          <label htmlFor={sortId} className="font-medium">
            並び順
          </label>
          <select
            id={sortId}
            value={condition.sort}
            onChange={(e) => update({ sort: e.target.value as SpotSort })}
            className="h-8 rounded-md border bg-background px-2"
          >
            {(Object.keys(SORT_LABELS) as SpotSort[]).map((sort) => (
              <option key={sort} value={sort}>
                {SORT_LABELS[sort]}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section aria-labelledby="search-results-heading" className="space-y-4">
        <h2 id="search-results-heading" className="text-xl font-semibold">
          検索結果
        </h2>
        {query ? (
          <SpotList
            queryKey={spotKeys.search(query)}
            fetchPage={(cursor) => fetchSpotSearch(query, cursor)}
            emptyMessage="該当するスポットはありませんでした。条件を変えて検索してください。"
            label="検索結果"
          />
        ) : (
          !areas.isError && (
            <p role="status" className="text-sm text-muted-foreground">
              読み込み中…
            </p>
          )
        )}
      </section>
    </section>
  )
}

function KeywordForm({ initial, onSubmit }: { initial: string; onSubmit: (q: string) => void }) {
  const [value, setValue] = useState(initial)
  const inputId = useId()

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(value.trim())
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="space-y-1.5">
      <label htmlFor={inputId} className="text-sm font-medium">
        キーワード（タイトル・場所）
      </label>
      <div className="flex gap-2">
        <Input
          id={inputId}
          type="search"
          value={value}
          maxLength={MAX_KEYWORD_LENGTH}
          placeholder="例: 温泉、清水寺"
          onChange={(e) => setValue(e.target.value)}
        />
        <Button type="submit">
          <Search aria-hidden />
          検索
        </Button>
      </div>
    </form>
  )
}
