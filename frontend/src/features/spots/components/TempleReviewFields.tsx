import { Star } from 'lucide-react'
import { useId } from 'react'
import { cn } from '@/lib/utils'
import {
  MAX_RATING,
  RATING_ITEMS,
  TEMPLE_FEATURE_LABELS,
  TEMPLE_FEATURES,
  type TempleFeature,
  type TempleRatings,
} from '../temple'

const SCORES = Array.from({ length: MAX_RATING }, (_, i) => i + 1)

type RatingInputProps = {
  label: string
  value: number | null
  onChange: (value: number | null) => void
}

/**
 * 1項目の5段階評価。星はラジオボタン（左右キーで点数を変えられる）。「未評価」を選ぶと評価を外せる。
 */
function RatingInput({ label, value, onChange }: RatingInputProps) {
  const name = useId()
  const labelId = `${name}-label`
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
      <span id={labelId} className="w-52 text-sm">
        {label}
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="flex items-center gap-0.5">
        {SCORES.map((score) => (
          // 透明なラジオボタンを星に重ね、星全体を押せる・キーボードで選べるようにする
          <label key={score} className="relative inline-flex cursor-pointer">
            <input
              type="radio"
              name={name}
              value={score}
              checked={value === score}
              onChange={() => onChange(score)}
              aria-label={`${score}点`}
              className="peer absolute inset-0 m-0 size-full cursor-pointer opacity-0"
            />
            <Star
              aria-hidden
              className={cn(
                'size-6 rounded-sm peer-focus-visible:ring-2 peer-focus-visible:ring-ring',
                value !== null && score <= value
                  ? 'fill-amber-400 text-amber-500'
                  : 'text-muted-foreground/50',
              )}
            />
          </label>
        ))}
        <label className="ml-2 flex cursor-pointer items-center gap-1 text-xs text-muted-foreground">
          <input
            type="radio"
            name={name}
            value=""
            checked={value === null}
            onChange={() => onChange(null)}
            className="size-3.5 accent-primary"
          />
          未評価
        </label>
      </div>
    </div>
  )
}

/** 投稿フォームのお寺の評価（4項目・各5段階。すべて任意）。 */
export function TempleRatingsInput({
  value,
  onChange,
}: {
  value: TempleRatings
  onChange: (value: TempleRatings) => void
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">お寺の評価（任意・5段階）</legend>
      <div className="space-y-2 rounded-lg border p-3">
        {RATING_ITEMS.map(({ key, label }) => (
          <RatingInput
            key={key}
            label={label}
            value={value[key]}
            onChange={(score) => onChange({ ...value, [key]: score })}
          />
        ))}
      </div>
    </fieldset>
  )
}

/** 投稿フォームのお寺の特徴タグ（複数選択・任意）。 */
export function TempleFeaturePicker({
  value,
  onChange,
  legend = 'お寺の特徴（任意・複数選択可）',
}: {
  value: TempleFeature[]
  onChange: (value: TempleFeature[]) => void
  legend?: string
}) {
  const toggle = (feature: TempleFeature) =>
    onChange(
      value.includes(feature)
        ? value.filter((f) => f !== feature)
        : TEMPLE_FEATURES.filter((f) => f === feature || value.includes(f)),
    )
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">{legend}</legend>
      <div className="flex flex-wrap gap-x-5 gap-y-2">
        {TEMPLE_FEATURES.map((feature) => (
          <label key={feature} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4 accent-primary"
              checked={value.includes(feature)}
              onChange={() => toggle(feature)}
            />
            {TEMPLE_FEATURE_LABELS[feature]}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
