import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, meFixture, otherUser, spotFixture } from '@/test/msw/handlers'
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
const third = { id: 3, username: 'jiro', displayName: '次郎', avatarUrl: null }

function commentFixture(
  id: number,
  body: string,
  author = otherUser,
  overrides: Record<string, unknown> = {},
) {
  return {
    id,
    spotId: 100,
    body,
    author,
    edited: false,
    createdAt: '2026-11-11T03:00:00Z',
    updatedAt: '2026-11-11T03:00:00Z',
    ...overrides,
  }
}

function givenSpot(overrides: Record<string, unknown> = {}) {
  server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture(overrides))))
}

function commentsList() {
  return screen.getByRole('list', { name: 'コメント' })
}

function commentOf(name: string) {
  return screen.getByRole('article', { name: `${name}のコメント` })
}

describe('コメント一覧', () => {
  it('古い順に表示し、「さらに表示」で続きを読み込む。編集済みには印を付ける', async () => {
    givenSpot({ commentCount: 3 })
    const cursors: (string | null)[] = []
    server.use(
      http.get('/api/v1/spots/100/comments', ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        cursors.push(cursor)
        return cursor === null
          ? HttpResponse.json({
              items: [
                commentFixture(1, '最初のコメント'),
                commentFixture(2, '2番目', third, { edited: true }),
              ],
              nextCursor: 'c1',
            })
          : HttpResponse.json({ items: [commentFixture(3, '3番目')], nextCursor: null })
      }),
    )
    renderApp('/spots/100')

    expect(await screen.findByRole('heading', { name: 'コメント（3件）' })).toBeInTheDocument()
    const items = await within(await screen.findByRole('list', { name: 'コメント' })).findAllByRole('article')
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('最初のコメント'),
      expect.stringContaining('2番目'),
    ])
    expect(within(commentOf('次郎')).getByText('（編集済み）')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'さらに表示' }))

    expect(await within(commentsList()).findByText('3番目')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'さらに表示' })).not.toBeInTheDocument()
    expect(cursors).toEqual([null, 'c1'])
  })

  it('本文の HTML はタグとして解釈せず、そのまま文字として表示する（XSS 対策）。改行も保つ', async () => {
    givenSpot()
    server.use(
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({
          items: [commentFixture(1, '<img src=x onerror=alert(1)><script>alert(2)</script>\n2行目')],
          nextCursor: null,
        }),
      ),
    )
    const { container } = renderApp('/spots/100')

    const body = await screen.findByText(/<script>alert\(2\)<\/script>/)
    expect(body.textContent).toBe('<img src=x onerror=alert(1)><script>alert(2)</script>\n2行目')
    expect(container.querySelector('article img[src="x"]')).toBeNull()
    expect(container.querySelector('article script')).toBeNull()
  })

  it('未ログインなら入力欄の代わりに「ログインしてコメントする」を表示する', async () => {
    givenSpot()
    renderApp('/spots/100')

    const link = await screen.findByRole('link', { name: 'ログインしてコメントする' })
    expect(link).toHaveAttribute('href', '/login?redirect=%2Fspots%2F100')
    expect(screen.queryByLabelText('コメントを書く')).not.toBeInTheDocument()
    expect(await screen.findByText('まだコメントはありません。')).toBeInTheDocument()
  })
})

describe('コメント投稿', () => {
  it('投稿すると一覧と件数を取り直し、入力欄を空にする', async () => {
    givenLoggedIn()
    let posted: unknown = null
    let commentCount = 0
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ commentCount }))),
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({
          items: posted ? [commentFixture(9, '何時ごろ行きましたか？', me)] : [],
          nextCursor: null,
        }),
      ),
      http.post('/api/v1/spots/100/comments', async ({ request }) => {
        posted = await request.json()
        commentCount = 1
        return HttpResponse.json(commentFixture(9, '何時ごろ行きましたか？', me), { status: 201 })
      }),
    )
    renderApp('/spots/100')

    const input = await screen.findByLabelText('コメントを書く')
    await userEvent.type(input, '何時ごろ行きましたか？')
    expect(screen.getByText('残り 989 文字')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'コメントする' }))

    const list = await screen.findByRole('list', { name: 'コメント' })
    expect(within(list).getByText('何時ごろ行きましたか？')).toBeInTheDocument()
    expect(posted).toEqual({ body: '何時ごろ行きましたか？' })
    expect(await screen.findByRole('heading', { name: 'コメント（1件）' })).toBeInTheDocument()
    expect(screen.getByLabelText('コメントを書く')).toHaveValue('')
  })

  it('空白のみ・1001文字は送信前にエラーを表示する', async () => {
    givenLoggedIn()
    givenSpot()
    renderApp('/spots/100')

    await userEvent.type(await screen.findByLabelText('コメントを書く'), '   ')
    await userEvent.click(screen.getByRole('button', { name: 'コメントする' }))
    expect(await screen.findByText('コメントを入力してください')).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText('コメントを書く'))
    await userEvent.click(screen.getByLabelText('コメントを書く'))
    await userEvent.paste('あ'.repeat(1001))
    await userEvent.click(screen.getByRole('button', { name: 'コメントする' }))
    expect(await screen.findByText('コメントは1000文字以内で入力してください')).toBeInTheDocument()
    expect(screen.getByText('残り -1 文字')).toBeInTheDocument()
  })
})

describe('コメントの編集・削除', () => {
  it('自分のコメントは編集でき、保存すると PATCH して一覧を取り直す', async () => {
    givenLoggedIn()
    givenSpot({ author: otherUser })
    let body = '誤字あり'
    let sent: unknown = null
    server.use(
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({
          items: [commentFixture(1, body, me, { edited: body !== '誤字あり' })],
          nextCursor: null,
        }),
      ),
      http.patch('/api/v1/comments/1', async ({ request }) => {
        sent = await request.json()
        body = '修正しました'
        return HttpResponse.json(commentFixture(1, body, me, { edited: true }))
      }),
    )
    renderApp('/spots/100')

    const mine = await screen.findByRole('article', { name: `${me.displayName}のコメント` })
    await userEvent.click(within(mine).getByRole('button', { name: '編集' }))
    const input = within(mine).getByLabelText('コメントを編集')
    expect(input).toHaveValue('誤字あり')
    await userEvent.clear(input)
    await userEvent.type(input, '修正しました')
    await userEvent.click(within(mine).getByRole('button', { name: '保存する' }))

    expect(await within(commentsList()).findByText('修正しました')).toBeInTheDocument()
    expect(within(commentOf(me.displayName)).getByText('（編集済み）')).toBeInTheDocument()
    expect(sent).toEqual({ body: '修正しました' })
  })

  it('他人のコメントには編集・削除を出さない。ただし自分の投稿についたコメントは削除できる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({ items: [commentFixture(1, 'はなこのコメント')], nextCursor: null }),
      ),
    )
    // 他人のスポット
    givenSpot({ author: third })
    const first = renderApp('/spots/100')
    const others = await screen.findByRole('article', { name: 'はなこのコメント' })
    expect(within(others).queryByRole('button', { name: '編集' })).not.toBeInTheDocument()
    expect(within(others).queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
    first.unmount()

    // 自分のスポット
    givenSpot()
    renderApp('/spots/100')
    const onMySpot = await screen.findByRole('article', { name: 'はなこのコメント' })
    expect(within(onMySpot).queryByRole('button', { name: '編集' })).not.toBeInTheDocument()
    expect(within(onMySpot).getByRole('button', { name: '削除' })).toBeInTheDocument()
  })

  it('確認ダイアログで削除すると DELETE して一覧から消える', async () => {
    givenLoggedIn()
    givenSpot({ author: otherUser })
    let deleted = false
    server.use(
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({
          items: deleted ? [] : [commentFixture(1, '消すコメント', me)],
          nextCursor: null,
        }),
      ),
      http.delete('/api/v1/comments/1', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/spots/100')

    const mine = await screen.findByRole('article', { name: `${me.displayName}のコメント` })
    await userEvent.click(within(mine).getByRole('button', { name: '削除' }))
    const dialog = await screen.findByRole('alertdialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('まだコメントはありません。')).toBeInTheDocument()
    expect(deleted).toBe(true)
  })

  it('削除に失敗したらトーストで知らせる', async () => {
    givenLoggedIn()
    givenSpot({ author: otherUser })
    server.use(
      http.get('/api/v1/spots/100/comments', () =>
        HttpResponse.json({ items: [commentFixture(1, '消すコメント', me)], nextCursor: null }),
      ),
      http.delete('/api/v1/comments/1', () =>
        HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 }),
      ),
    )
    renderApp('/spots/100')

    const mine = await screen.findByRole('article', { name: `${me.displayName}のコメント` })
    await userEvent.click(within(mine).getByRole('button', { name: '削除' }))
    await userEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: '削除する' }),
    )

    expect(
      await screen.findByText('コメントを削除できませんでした。時間をおいて再度お試しください'),
    ).toBeInTheDocument()
    await waitFor(() => expect(within(commentsList()).getByText('消すコメント')).toBeInTheDocument())
  })
})

describe('スポットカード', () => {
  it('コメント件数を表示する', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [{ ...spotFixture({ commentCount: 4 }), impressionExcerpt: null, coverPhotoUrl: null }],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    expect(await screen.findByText('コメント 4件')).toBeInTheDocument()
  })
})
