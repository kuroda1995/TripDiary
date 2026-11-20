import { describe, expect, it } from 'vitest'
import { areasFixture } from '@/test/msw/handlers'
import {
  indexAreasBySlug,
  parseSearchParams,
  searchUrlForArea,
  searchUrlForTempleFeature,
  toSearchParams,
} from './searchParams'

describe('検索条件と URL の変換', () => {
  it('エリア・キーワード・お寺の特徴・並び順を読み、往復しても同じになる', () => {
    const params = new URLSearchParams('areas=kyoto,osaka&q=温泉&features=GOSHUIN,GARDEN&sort=popular')
    const condition = parseSearchParams(params)

    expect(condition).toEqual({
      areas: ['kyoto', 'osaka'],
      q: '温泉',
      features: ['GOSHUIN', 'GARDEN'],
      sort: 'popular',
    })
    expect(toSearchParams(condition).toString()).toBe(params.toString())
  })

  it('既定値（条件なし・新着順）は URL に出さない', () => {
    expect(toSearchParams({ areas: [], q: '  ', features: [], sort: 'newest' }).toString()).toBe('')
    expect(parseSearchParams(new URLSearchParams())).toEqual({
      areas: [],
      q: '',
      features: [],
      sort: 'newest',
    })
  })

  it('お寺の特徴は知らない値・重複を除き、表示順にそろえる', () => {
    const condition = parseSearchParams(new URLSearchParams('features=QUIET,ONSEN,GOSHUIN,QUIET,'))

    expect(condition.features).toEqual(['GOSHUIN', 'QUIET'])
  })

  it('不正な並び順は新着順、エリアの空要素・重複は除き、上限を超えた分とキーワードの超過分は切り捨てる', () => {
    const many = Array.from({ length: 12 }, (_, i) => `a${i}`).join(',')
    const condition = parseSearchParams(
      new URLSearchParams(`areas=kyoto,,kyoto,${many}&sort=likes&q=${'あ'.repeat(120)}`),
    )

    expect(condition.sort).toBe('newest')
    expect(condition.areas).toHaveLength(10)
    expect(condition.areas.slice(0, 2)).toEqual(['kyoto', 'a0'])
    expect(condition.q).toHaveLength(100)
  })

  it('スラッグから地方・都道府県のタグを引ける', () => {
    const bySlug = indexAreasBySlug(areasFixture)

    expect(bySlug.get('kansai')?.id).toBe(1)
    expect(bySlug.get('okinawa')?.id).toBe(23)
    expect(bySlug.has('nowhere')).toBe(false)
  })

  it('タグ1つで検索する URL を作る', () => {
    expect(searchUrlForArea('kyoto')).toBe('/search?areas=kyoto')
    expect(searchUrlForTempleFeature('GOSHUIN')).toBe('/search?features=GOSHUIN')
  })
})
