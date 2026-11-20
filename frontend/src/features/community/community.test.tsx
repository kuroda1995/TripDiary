import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  authResponse,
  errorResponse,
  otherUser,
  profileFixture,
  spotFixture,
  spotSummaryFixture,
} from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

function helpfulButton(container: HTMLElement = document.body) {
  return within(container).getByRole('button', { name: /参考になった/ })
}

describe('参考になったボタン', () => {
  it('押すとすぐに切り替わり PUT で付け、もう一度押すと DELETE で外す（行ってみたい！とは別）', async () => {
    givenLoggedIn()
    const requests: string[] = []
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(spotFixture({ author: otherUser, helpfulCount: 2, likeCount: 7 })),
      ),
      http.put('/api/v1/spots/100/helpful', () => {
        requests.push('PUT')
        return HttpResponse.json({ spotId: 100, helpful: true, helpfulCount: 3 })
      }),
      http.delete('/api/v1/spots/100/helpful', () => {
        requests.push('DELETE')
        return HttpResponse.json({ spotId: 100, helpful: false, helpfulCount: 2 })
      }),
    )
    renderApp('/spots/100')

    const button = await screen.findByRole('button', { name: /参考になった/ })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    expect(button).toHaveTextContent('2')
    expect(screen.getByRole('button', { name: /行ってみたい！/ })).toHaveTextContent('7')

    await userEvent.click(button)
    expect(helpfulButton()).toHaveAttribute('aria-pressed', 'true')
    await waitFor(() => expect(helpfulButton()).toBeEnabled())
    expect(helpfulButton()).toHaveTextContent('3')

    await userEvent.click(helpfulButton())
    await waitFor(() => expect(helpfulButton()).toHaveAttribute('aria-pressed', 'false'))
    await waitFor(() => expect(helpfulButton()).toBeEnabled())
    expect(helpfulButton()).toHaveTextContent('2')
    expect(requests).toEqual(['PUT', 'DELETE'])
    // 行ってみたい！の件数は変わらない
    expect(screen.getByRole('button', { name: /行ってみたい！/ })).toHaveTextContent('7')
  })

  it('API が失敗したら元に戻してトーストで知らせる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ helpfulCount: 1 }))),
      http.put('/api/v1/spots/100/helpful', async () => {
        await delay(20)
        return HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 })
      }),
    )
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: /参考になった/ }))

    expect(
      await screen.findByText('参考になったを更新できませんでした。時間をおいて再度お試しください'),
    ).toBeInTheDocument()
    expect(helpfulButton()).toHaveAttribute('aria-pressed', 'false')
    expect(helpfulButton()).toHaveTextContent('1')
  })

  it('未ログインで押すとログイン画面へ移動する', async () => {
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: /参考になった/ }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('一覧のカードからも付けられる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({ items: [spotSummaryFixture(1, '瑞龍寺')], nextCursor: null }),
      ),
      http.put('/api/v1/spots/1/helpful', () =>
        HttpResponse.json({ spotId: 1, helpful: true, helpfulCount: 1 }),
      ),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    await userEvent.click(helpfulButton(list))

    await waitFor(() => expect(helpfulButton(list)).toBeEnabled())
    expect(helpfulButton(list)).toHaveAttribute('aria-pressed', 'true')
    expect(helpfulButton(list)).toHaveTextContent('1')
  })
})

describe('行きたいボタン', () => {
  it('押すと「行きたい」に保存し（PUT WANT_TO_GO）、もう一度押すと外す（DELETE）', async () => {
    givenLoggedIn()
    const requests: unknown[] = []
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ author: otherUser }))),
      http.put('/api/v1/spots/100/status', async ({ request }) => {
        requests.push(await request.json())
        return HttpResponse.json({
          spotId: 100,
          status: 'WANT_TO_GO',
          visitedAt: null,
          updatedAt: '2026-11-20T00:00:00Z',
        })
      }),
      http.delete('/api/v1/spots/100/status', () => {
        requests.push('DELETE')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/spots/100')

    const button = await screen.findByRole('button', { name: '行きたい' })
    expect(button).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(button)
    await waitFor(() => expect(screen.getByRole('button', { name: '行きたい' })).toBeEnabled())
    expect(screen.getByRole('button', { name: '行きたい' })).toHaveAttribute('aria-pressed', 'true')
    // マイリストの選択にも反映される
    expect(screen.getByRole('combobox', { name: 'マイリスト' })).toHaveValue('WANT_TO_GO')

    await userEvent.click(screen.getByRole('button', { name: '行きたい' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '行きたい' })).toHaveAttribute('aria-pressed', 'false'),
    )
    expect(requests).toEqual([{ status: 'WANT_TO_GO' }, 'DELETE'])
  })

  it('訪問済みのスポットでは出さない（訪問済みを消さないため）', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ myStatus: 'VISITED' }))))
    renderApp('/spots/100')

    expect(await screen.findByRole('combobox', { name: 'マイリスト' })).toHaveValue('VISITED')
    expect(screen.queryByRole('button', { name: '行きたい' })).not.toBeInTheDocument()
  })

  it('保存したスポットはマイページの「行きたい」タブで確認できる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/users/me', () => HttpResponse.json(authResponse().user)),
      http.get('/api/v1/me/spot-statuses', () =>
        HttpResponse.json({
          items: [
            {
              status: 'WANT_TO_GO',
              visitedAt: null,
              updatedAt: '2026-11-20T00:00:00Z',
              spot: spotSummaryFixture(5, '瑞龍寺', { myStatus: 'WANT_TO_GO' }),
            },
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/me')

    expect(await screen.findByRole('tab', { name: /行きたい/ })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByRole('link', { name: '瑞龍寺' })).toBeInTheDocument()
  })
})

describe('コミュニティ称号', () => {
  const master = { ...otherUser, badge: 'MASTER' }

  it('スポットの投稿者・カード・コメントのユーザー名の横に称号を出す', async () => {
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ author: master }))),
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({
          items: [
            {
              id: 1,
              spotId: 100,
              body: '参考になりました',
              author: { id: 3, username: 'jiro', displayName: '次郎', avatarUrl: null, badge: 'BEGINNER' },
              edited: false,
              createdAt: '2026-11-20T00:00:00Z',
              updatedAt: '2026-11-20T00:00:00Z',
            },
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/spots/100')

    expect(await screen.findByText('お寺マイスター')).toBeInTheDocument()
    const comment = await screen.findByRole('article', { name: '次郎のコメント' })
    expect(within(comment).getByText('参拝ビギナー')).toBeInTheDocument()
  })

  it('一覧カードと掲示板にも称号を出し、称号がなければ何も出さない', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [
            spotSummaryFixture(1, '瑞龍寺', { author: { ...otherUser, badge: 'PILGRIM' } }),
            spotSummaryFixture(2, '清水寺', { author: otherUser }),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    expect(within(list).getAllByText('御朱印巡り人')).toHaveLength(1)
    expect(within(list).queryByText('参拝ビギナー')).not.toBeInTheDocument()
  })

  it('プロフィールに称号とコミュニティ活動数を出す', async () => {
    server.use(
      http.get('/api/v1/users/hanako', () =>
        HttpResponse.json(profileFixture({ activityCount: 6, badge: 'MASTER' })),
      ),
    )
    renderApp('/users/hanako')

    expect(await screen.findByText('お寺マイスター')).toHaveAttribute(
      'title',
      '投稿・コメント・掲示板の書き込みが5件以上',
    )
    const counts = screen.getByRole('list', { name: '件数' })
    expect(within(counts).getByText('コミュニティ活動').parentElement).toHaveTextContent('6 コミュニティ活動')
  })
})
