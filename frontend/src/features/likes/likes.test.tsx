import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, spotFixture, spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

function likeButton(container: HTMLElement = document.body) {
  return within(container).getByRole('button', { name: /行ってみたい！/ })
}

describe('行ってみたい！ボタン', () => {
  it('押すとすぐに表示が切り替わり（楽観的更新）、PUT で付け、もう一度押すと DELETE で外す', async () => {
    givenLoggedIn()
    const requests: string[] = []
    let release: () => void = () => {}
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ likeCount: 3 }))),
      http.put('/api/v1/spots/100/like', async () => {
        requests.push('PUT')
        // 応答を待たせて、その間に表示が切り替わっていることを確認する
        await new Promise<void>((resolve) => (release = resolve))
        return HttpResponse.json({ spotId: 100, liked: true, likeCount: 4 })
      }),
      http.delete('/api/v1/spots/100/like', () => {
        requests.push('DELETE')
        return HttpResponse.json({ spotId: 100, liked: false, likeCount: 3 })
      }),
    )
    renderApp('/spots/100')

    const button = await screen.findByRole('button', { name: /行ってみたい！/ })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    expect(button).toHaveTextContent('3')

    await userEvent.click(button)
    // サーバーの応答前に切り替わる
    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton()).toHaveTextContent('4')

    release()
    await waitFor(() => expect(likeButton()).toBeEnabled())
    expect(likeButton()).toHaveTextContent('4')

    await userEvent.click(likeButton())
    await waitFor(() => expect(likeButton()).toHaveAttribute('aria-pressed', 'false'))
    await waitFor(() => expect(likeButton()).toBeEnabled())
    expect(likeButton()).toHaveTextContent('3')
    expect(requests).toEqual(['PUT', 'DELETE'])
  })

  it('API が失敗したら表示を元に戻し、トーストでエラーを知らせる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ likeCount: 3 }))),
      http.put('/api/v1/spots/100/like', async () => {
        await delay(20)
        return HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 })
      }),
    )
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: /行ってみたい！/ }))
    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')

    expect(
      await screen.findByText('行ってみたい！を更新できませんでした。時間をおいて再度お試しください'),
    ).toBeInTheDocument()
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
    expect(likeButton()).toHaveTextContent('3')
  })

  it('未ログインで押すとログイン画面へ移動する（API は呼ばない）', async () => {
    let called = false
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())),
      http.put('/api/v1/spots/100/like', () => {
        called = true
        return HttpResponse.json({})
      }),
    )
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: /行ってみたい！/ }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('一覧のカードからも付けられ、件数が反映される', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [
            spotSummaryFixture(1, '清水寺', { likeCount: 0 }),
            spotSummaryFixture(2, '伏見稲荷', { likeCount: 5, likedByMe: true }),
          ],
          nextCursor: null,
        }),
      ),
      http.put('/api/v1/spots/1/like', () => HttpResponse.json({ spotId: 1, liked: true, likeCount: 1 })),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    // カードの中にもエリアタグの一覧（li）があるため、直下の要素だけを見る
    const [first, second] = Array.from(list.children) as HTMLElement[]
    expect(likeButton(second)).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton(second)).toHaveTextContent('5')

    await userEvent.click(likeButton(first))

    await waitFor(() => expect(likeButton(first)).toBeEnabled())
    expect(likeButton(first)).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton(first)).toHaveTextContent('1')
  })
})

describe('行ってみたい！した投稿', () => {
  it('マイページに一覧を表示する', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/users/me', () => HttpResponse.json(authResponse().user)),
      http.get('/api/v1/me/likes', () =>
        HttpResponse.json({
          items: [spotSummaryFixture(7, '行きたい温泉', { likeCount: 2, likedByMe: true })],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/me?tab=likes')

    const list = await screen.findByRole('list', { name: '行ってみたい！した投稿' })
    expect(within(list).getByRole('link', { name: '行きたい温泉' })).toBeInTheDocument()
    expect(likeButton(list)).toHaveAttribute('aria-pressed', 'true')
  })

  it('まだ無ければ案内を表示する', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/users/me', () => HttpResponse.json(authResponse().user)))
    renderApp('/me?tab=likes')

    expect(await screen.findByText(/まだ行ってみたい！した投稿はありません/)).toBeInTheDocument()
  })
})
