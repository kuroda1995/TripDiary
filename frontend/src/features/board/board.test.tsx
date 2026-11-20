import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, meFixture, otherUser } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

const me = {
  id: meFixture.id,
  username: meFixture.username,
  displayName: meFixture.displayName,
  avatarUrl: null,
}

function topicFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 12,
    title: 'おすすめのお寺相談',
    body: '紅葉の時期に行くならどこがいいですか？\n京都で探しています。',
    author: otherUser,
    replyCount: 0,
    lastActivityAt: '2026-11-19T03:00:00Z',
    createdAt: '2026-11-19T03:00:00Z',
    ...overrides,
  }
}

function topicSummary(id: number, title: string, overrides: Record<string, unknown> = {}) {
  const { body, ...rest } = topicFixture({ id, title, ...overrides })
  return { ...rest, bodyExcerpt: body }
}

function replyFixture(id: number, body: string, author = otherUser) {
  return { id, topicId: 12, body, author, createdAt: '2026-11-19T04:00:00Z' }
}

describe('コミュニティ広場（トピック一覧）', () => {
  it('ヘッダーのリンクから開け、トピックを返信数・最終更新日時つきで表示する', async () => {
    server.use(
      http.get('/api/v1/board-topics', () =>
        HttpResponse.json({
          items: [
            topicSummary(12, 'おすすめのお寺相談', { replyCount: 3 }),
            topicSummary(11, '御朱印情報交換'),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    await userEvent.click(await screen.findByRole('link', { name: 'コミュニティ広場' }))

    expect(await screen.findByRole('heading', { name: 'お寺コミュニティ広場' })).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'トピック' })
    expect(within(list).getByRole('link', { name: 'おすすめのお寺相談' })).toHaveAttribute(
      'href',
      '/community/topics/12',
    )
    expect(within(list).getByText('返信 3件')).toBeInTheDocument()
    expect(within(list).getByRole('link', { name: '御朱印情報交換' })).toBeInTheDocument()
  })

  it('未ログインならトピック作成フォームの代わりにログインへの案内を出す', async () => {
    renderApp('/community')

    expect(await screen.findByRole('link', { name: 'ログインしてトピックを作成する' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcommunity',
    )
    expect(screen.queryByRole('button', { name: 'トピックを作成する' })).not.toBeInTheDocument()
    expect(await screen.findByText(/まだトピックはありません/)).toBeInTheDocument()
  })

  it('トピックを作成すると、そのトピックの画面へ移動する', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    server.use(
      http.post('/api/v1/board-topics', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(topicFixture({ id: 30, title: '御朱印情報交換', author: me }), {
          status: 201,
        })
      }),
      http.get('/api/v1/board-topics/30', () =>
        HttpResponse.json(topicFixture({ id: 30, title: '御朱印情報交換', author: me })),
      ),
    )
    renderApp('/community')

    await userEvent.type(await screen.findByLabelText('タイトル'), '御朱印情報交換')
    await userEvent.type(screen.getByLabelText('本文'), '最近いただいた御朱印を教えてください')
    await userEvent.click(screen.getByRole('button', { name: 'トピックを作成する' }))

    expect(await screen.findByRole('heading', { name: '御朱印情報交換' })).toBeInTheDocument()
    expect(sentBody).toEqual({ title: '御朱印情報交換', body: '最近いただいた御朱印を教えてください' })
  })

  it('タイトル・本文が空なら送信前にエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/community')

    await userEvent.click(await screen.findByRole('button', { name: 'トピックを作成する' }))

    expect(await screen.findByText('タイトルを入力してください')).toBeInTheDocument()
    expect(screen.getByText('本文を入力してください')).toBeInTheDocument()
  })
})

describe('トピック画面（スレッド）', () => {
  it('本文（改行を保持）と返信を古い順に表示する', async () => {
    server.use(
      http.get('/api/v1/board-topics/12', () => HttpResponse.json(topicFixture({ replyCount: 2 }))),
      http.get('/api/v1/board-topics/12/replies', () =>
        HttpResponse.json({
          items: [
            replyFixture(1, '東福寺の通天橋がおすすめです'),
            replyFixture(2, '永観堂も<b>きれい</b>です'),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/community/topics/12')

    expect(await screen.findByRole('heading', { name: 'おすすめのお寺相談' })).toBeInTheDocument()
    expect(screen.getByText(/京都で探しています。/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '返信（2件）' })).toBeInTheDocument()
    const replies = await screen.findByRole('list', { name: '返信' })
    const items = within(replies).getAllByRole('article')
    expect(items[0]).toHaveTextContent('東福寺の通天橋がおすすめです')
    // HTML は文字列として表示する
    expect(items[1]).toHaveTextContent('永観堂も<b>きれい</b>です')
    expect(screen.getByRole('link', { name: 'ログインして返信する' })).toBeInTheDocument()
  })

  it('返信すると一覧を取り直して表示する', async () => {
    givenLoggedIn()
    const replies = [replyFixture(1, '東福寺がおすすめです')]
    server.use(
      http.get('/api/v1/board-topics/12', () =>
        HttpResponse.json(topicFixture({ replyCount: replies.length })),
      ),
      http.get('/api/v1/board-topics/12/replies', () =>
        HttpResponse.json({ items: replies, nextCursor: null }),
      ),
      http.post('/api/v1/board-topics/12/replies', async ({ request }) => {
        const { body } = (await request.json()) as { body: string }
        const created = replyFixture(2, body, me)
        replies.push(created)
        return HttpResponse.json(created, { status: 201 })
      }),
    )
    renderApp('/community/topics/12')

    await userEvent.type(await screen.findByLabelText('返信を書く'), '南禅寺もきれいでした')
    await userEvent.click(screen.getByRole('button', { name: '返信する' }))

    const list = screen.getByRole('list', { name: '返信' })
    expect(await within(list).findByText('南禅寺もきれいでした')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '返信（2件）' })).toBeInTheDocument()
    expect(screen.getByLabelText('返信を書く')).toHaveValue('')
  })

  it('自分の返信は削除でき、他人の返信には削除ボタンを出さない（他人のトピック）', async () => {
    givenLoggedIn()
    let deleted: string | null = null
    server.use(
      http.get('/api/v1/board-topics/12', () => HttpResponse.json(topicFixture())),
      http.get('/api/v1/board-topics/12/replies', () =>
        HttpResponse.json({
          items: deleted
            ? [replyFixture(1, '他人の返信')]
            : [replyFixture(1, '他人の返信'), replyFixture(2, '自分の返信', me)],
          nextCursor: null,
        }),
      ),
      http.delete('/api/v1/board-replies/:id', ({ params }) => {
        deleted = String(params.id)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/community/topics/12')

    const others = await screen.findByRole('article', { name: 'はなこの返信' })
    expect(within(others).queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
    // 削除ボタンは自分の返信の1つだけ（他人のトピックなのでトピックの削除ボタンも出ない）
    expect(screen.getAllByRole('button', { name: '削除' })).toHaveLength(1)

    const mine = screen.getByRole('article', { name: `${meFixture.displayName}の返信` })
    await userEvent.click(within(mine).getByRole('button', { name: '削除' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '削除する' }))

    await waitFor(() => expect(screen.queryByText('自分の返信')).not.toBeInTheDocument())
    expect(deleted).toBe('2')
  })

  it('自分のトピックは削除でき、削除すると広場へ戻る', async () => {
    givenLoggedIn()
    let deleted = false
    server.use(
      http.get('/api/v1/board-topics/12', () => HttpResponse.json(topicFixture({ author: me }))),
      http.delete('/api/v1/board-topics/12', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/community/topics/12')

    await screen.findByRole('heading', { name: 'おすすめのお寺相談' })
    await userEvent.click(screen.getByRole('button', { name: '削除' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '削除する' }))

    expect(await screen.findByRole('heading', { name: 'お寺コミュニティ広場' })).toBeInTheDocument()
    expect(deleted).toBe(true)
  })

  it('存在しないトピックは「見つかりません」と表示する', async () => {
    server.use(
      http.get('/api/v1/board-topics/99', () =>
        HttpResponse.json(errorResponse('BOARD_TOPIC_NOT_FOUND', 'なし'), { status: 404 }),
      ),
    )
    renderApp('/community/topics/99')

    expect(await screen.findByRole('heading', { name: 'トピックが見つかりません' })).toBeInTheDocument()
  })
})
