import { afterEach, describe, expect, it, vi } from 'vitest'
import { emptySpotForm, spotSchema, toFormValues, toSpotInput } from './schemas'
import type { Spot } from './types'

const valid = { ...emptySpotForm, title: '清水寺', locationName: '京都', areaTagIds: [11] }

function errorsFor(values: Partial<typeof valid>) {
  const result = spotSchema.safeParse({ ...valid, ...values })
  return result.success
    ? {}
    : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('spotSchema（サーバーと同じ入力ルール）', () => {
  afterEach(() => vi.useRealTimers())

  it('正しい入力は通る', () => {
    expect(spotSchema.safeParse(valid).success).toBe(true)
  })

  it('タイトル・場所は空白のみ不可、文字数上限あり', () => {
    expect(errorsFor({ title: '   ' }).title).toBe('タイトルを入力してください')
    expect(errorsFor({ title: 'あ'.repeat(101) }).title).toBeDefined()
    expect(errorsFor({ locationName: 'あ'.repeat(201) }).locationName).toBeDefined()
  })

  it('エリアタグは1〜5個', () => {
    expect(errorsFor({ areaTagIds: [] }).areaTagIds).toBe('エリアタグを1つ以上選択してください')
    expect(errorsFor({ areaTagIds: [1, 2, 3, 4, 5, 6] }).areaTagIds).toBeDefined()
  })

  it('訪問日は日本時間の今日まで', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-11-11T15:30:00Z')) // 日本時間 11/12 00:30

    expect(errorsFor({ visitedOn: '2026-11-12' }).visitedOn).toBeUndefined()
    expect(errorsFor({ visitedOn: '2026-11-13' }).visitedOn).toBe('訪問日に未来の日付は指定できません')
  })

  it('感想は5000文字まで', () => {
    expect(errorsFor({ impression: 'あ'.repeat(5000) }).impression).toBeUndefined()
    expect(errorsFor({ impression: 'あ'.repeat(5001) }).impression).toBeDefined()
  })
})

describe('フォーム値とリクエストの変換', () => {
  it('空の感想・訪問日は null として送る', () => {
    expect(toSpotInput({ ...valid, impression: '  ', visitedOn: '' })).toMatchObject({
      impression: null,
      visitedOn: null,
    })
  })

  it('既存のスポットをフォームの初期値に変換する', () => {
    const spot = {
      title: 't',
      locationName: 'l',
      areaTags: [{ id: 11, name: '京都', slug: 'kyoto' }],
      impression: null,
      visitedOn: null,
      visibility: 'PRIVATE',
    } as unknown as Spot

    expect(toFormValues(spot)).toEqual({
      title: 't',
      locationName: 'l',
      areaTagIds: [11],
      impression: '',
      visitedOn: '',
      visibility: 'PRIVATE',
    })
  })
})
