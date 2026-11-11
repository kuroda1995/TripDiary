import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, spotFixture, spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())),
    http.get('/api/v1/users/me', () => HttpResponse.json(authResponse().user)),
  )
}

function statusSelect() {
  return screen.getByRole('combobox', { name: 'マイリスト' })
}

function myListItem(status: 'WANT_TO_GO' | 'VISITED', id: number, title: string, extra = {}) {
  return {
    status,
    visitedAt: status === 'VISITED' ? '2026-11-10T15:30:00Z' : null,
    updatedAt: '2026-11-10T15:30:00Z',
    spot: spotSummaryFixture(id, title, { myStatus: status }),
    ...extra,
  }
}

describe('ステータスの選択（スポット詳細）', () => {
  it('選んだ瞬間に切り替わり（楽観的更新）、PUT で設定し、「マイリスト未登録」に戻すと DELETE で解除する', async () => {
    givenLoggedIn()
    const requests: string[] = []
    let release: () => void = () => {}
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())),
      http.put('/api/v1/spots/100/status', async ({ request }) => {
        const body = (await request.json()) as { status: string }
        requests.push(`PUT ${body.status}`)
        await new Promise<void>((resolve) => (release = resolve))
        return HttpResponse.json({ spotId: 100, status: body.status, visitedAt: null, updatedAt: 'x' })
      }),
      http.delete('/api/v1/spots/100/status', () => {
        requests.push('DELETE')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/spots/100')

    await screen.findByRole('combobox', { name: 'マイリスト' })
    expect(statusSelect()).toHaveValue('')

    await userEvent.selectOptions(statusSelect(), 'WANT_TO_GO')
    // サーバーの応答前に切り替わる
    expect(statusSelect()).toHaveValue('WANT_TO_GO')
    release()
    await waitFor(() => expect(statusSelect()).toBeEnabled())
    expect(statusSelect()).toHaveValue('WANT_TO_GO')

    await userEvent.selectOptions(statusSelect(), '')
    await waitFor(() => expect(statusSelect()).toBeEnabled())
    expect(statusSelect()).toHaveValue('')
    expect(requests).toEqual(['PUT WANT_TO_GO', 'DELETE'])
  })

  it('API が失敗したら表示を元に戻し、トーストで知らせる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ myStatus: 'WANT_TO_GO' }))),
      http.put('/api/v1/spots/100/status', async () => {
        await delay(20)
        return HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 })
      }),
    )
    renderApp('/spots/100')

    await screen.findByRole('combobox', { name: 'マイリスト' })
    expect(statusSelect()).toHaveValue('WANT_TO_GO')
    await userEvent.selectOptions(statusSelect(), 'VISITED')

    expect(
      await screen.findByText('マイリストを更新できませんでした。時間をおいて再度お試しください'),
    ).toBeInTheDocument()
    expect(statusSelect()).toHaveValue('WANT_TO_GO')
  })

  it('未ログインでは「マイリストに追加」を押すとログイン画面へ移動する', async () => {
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: 'マイリストに追加' }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('一覧のカードにも表示する', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [spotSummaryFixture(3, '湯布院', { myStatus: 'VISITED' })],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    expect(await within(list).findByRole('combobox', { name: 'マイリスト' })).toHaveValue('VISITED')
  })
})

describe('マイページのマイリスト', () => {
  it('「行きたい」タブを件数付きで最初に表示し、タブを切り替えると URL に残る', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/me/spot-statuses/summary', () => HttpResponse.json({ visited: 34, wantToGo: 12 })),
      http.get('/api/v1/me/spot-statuses', ({ request }) => {
        const status = new URL(request.url).searchParams.get('status')
        return HttpResponse.json({
          items:
            status === 'VISITED'
              ? [myListItem('VISITED', 2, '行った温泉')]
              : [myListItem('WANT_TO_GO', 1, '行きたい滝')],
          nextCursor: null,
        })
      }),
    )
    renderApp('/me')

    const tabs = await screen.findByRole('tablist', { name: 'マイリスト' })
    expect(await within(tabs).findByRole('tab', { name: '行きたい（12）' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    const wantList = await screen.findByRole('list', { name: '行きたいのスポット' })
    expect(within(wantList).getByRole('link', { name: '行きたい滝' })).toBeInTheDocument()
    expect(within(wantList).getByText('2026年11月11日に追加')).toBeInTheDocument()

    await userEvent.click(within(tabs).getByRole('tab', { name: '訪問済み（34）' }))
    const visitedList = await screen.findByRole('list', { name: '訪問済みのスポット' })
    expect(within(visitedList).getByRole('link', { name: '行った温泉' })).toBeInTheDocument()
    expect(within(visitedList).getByText('2026年11月11日に訪問済みにしました')).toBeInTheDocument()
    // 「訪問済み」には「行った！」ボタンを出さない
    expect(within(visitedList).queryByRole('button', { name: /行った！/ })).not.toBeInTheDocument()

    // 左右キーでもタブを移動できる
    await userEvent.keyboard('{ArrowRight}')
    expect(within(tabs).getByRole('tab', { name: '行ってみたい！した投稿' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(within(tabs).getByRole('tab', { name: '行ってみたい！した投稿' })).toHaveFocus()
  })

  it('「行った！」を押すと訪問済みにして、リストと件数を取り直す', async () => {
    givenLoggedIn()
    let visited = false
    server.use(
      http.get('/api/v1/me/spot-statuses/summary', () =>
        HttpResponse.json(visited ? { visited: 1, wantToGo: 0 } : { visited: 0, wantToGo: 1 }),
      ),
      http.get('/api/v1/me/spot-statuses', ({ request }) => {
        const status = new URL(request.url).searchParams.get('status')
        const items = status === 'WANT_TO_GO' && !visited ? [myListItem('WANT_TO_GO', 1, '行きたい滝')] : []
        return HttpResponse.json({ items, nextCursor: null })
      }),
      http.put('/api/v1/spots/1/status', async ({ request }) => {
        expect(await request.json()).toEqual({ status: 'VISITED' })
        visited = true
        return HttpResponse.json({ spotId: 1, status: 'VISITED', visitedAt: 'x', updatedAt: 'x' })
      }),
    )
    renderApp('/me')

    await userEvent.click(
      await screen.findByRole('button', { name: '「行きたい滝」に行った！（訪問済みにする）' }),
    )

    expect(await screen.findByText('「行きたい滝」を訪問済みにしました')).toBeInTheDocument()
    expect(await screen.findByText(/まだ「行きたい」スポットはありません/)).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: '訪問済み（1）' })).toBeInTheDocument()
  })

  it('仕様書のパス /me/lists?status=visited は「訪問済み」タブへ移動する', async () => {
    givenLoggedIn()
    renderApp('/me/lists?status=visited')

    expect(await screen.findByRole('tab', { name: /訪問済み/ })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('まだ「訪問済み」のスポットはありません。')).toBeInTheDocument()
  })

  it('マイリストを読み込めなければ再読み込みできる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/me/spot-statuses', () =>
        HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 }),
      ),
    )
    renderApp('/me')

    expect(await screen.findByText('マイリストを読み込めませんでした。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '再読み込み' })).toBeInTheDocument()
  })
})
