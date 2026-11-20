import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { TapButton } from '@/components/TapButton'
import { spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'
import { cardRevealProps } from './motion'

describe('cardRevealProps（一覧カードの表示アニメーション）', () => {
  it('画面に入ったときに一度だけ、下からフェードインする', () => {
    const props = cardRevealProps(0)
    expect(props.initial).toEqual({ opacity: 0, y: 24 })
    expect(props.whileInView).toEqual({ opacity: 1, y: 0 })
    expect(props.viewport.once).toBe(true)
  })

  it('順番に少しずつ遅らせ、4枚ごとに遅れをリセットする（下の方のカードを待たせない）', () => {
    const delays = [0, 1, 2, 3, 4, 5].map((i) => cardRevealProps(i).transition.delay)
    expect(delays[0]).toBe(0)
    expect(delays[1]).toBeGreaterThan(delays[0])
    expect(delays[3]).toBeGreaterThan(delays[2])
    expect(delays[4]).toBe(0)
    expect(delays[5]).toBe(delays[1])
  })
})

describe('TapButton（タップで弾むボタン）', () => {
  it('ネイティブの button として描画され、クリック・無効化・属性が通常の Button と同じく働く', async () => {
    const onClick = vi.fn<() => void>()
    const { rerender } = render(
      <TapButton type="button" variant="outline" aria-pressed={false} onClick={onClick}>
        行ってみたい！
      </TapButton>,
    )

    const button = screen.getByRole('button', { name: '行ってみたい！' })
    expect(button.tagName).toBe('BUTTON')
    expect(button).toHaveAttribute('data-slot', 'button')
    expect(button).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(button)
    expect(onClick).toHaveBeenCalledTimes(1)

    rerender(
      <TapButton type="button" disabled onClick={onClick}>
        行ってみたい！
      </TapButton>,
    )
    expect(screen.getByRole('button', { name: '行ってみたい！' })).toBeDisabled()
  })
})

describe('スポット一覧のアニメーション', () => {
  it('カードは画面に入ると表示される（透明のまま残らない）', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [
            spotSummaryFixture(1, '清水寺'),
            spotSummaryFixture(2, '金閣寺'),
            spotSummaryFixture(3, '銀閣寺'),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    // カードの中にもタグの一覧があるため、一覧の直下（カード）だけを見る
    await within(list).findByText('銀閣寺')
    const items = Array.from(list.children) as HTMLElement[]
    expect(items).toHaveLength(3)
    for (const item of items) {
      expect(item).toBeVisible()
      expect(item.style.opacity).toBe('1')
    }
  })
})
