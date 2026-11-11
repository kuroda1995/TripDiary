import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  authResponse,
  errorResponse,
  meFixture,
  profileFixture,
  spotSummaryFixture,
} from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

function givenProfile(overrides: Record<string, unknown> = {}) {
  server.use(http.get('/api/v1/users/hanako', () => HttpResponse.json(profileFixture(overrides))))
}

function counts() {
  return screen.getByRole('list', { name: '件数' })
}

describe('プロフィール画面', () => {
  it('アイコン・表示名・自己紹介・件数・投稿一覧とフォローボタンを表示する', async () => {
    givenLoggedIn()
    givenProfile({ bio: '週末は温泉めぐり♨', spotCount: 28, followingCount: 15, followerCount: 1200 })
    server.use(
      http.get('/api/v1/users/hanako/spots', () =>
        HttpResponse.json({ items: [spotSummaryFixture(3, '湯布院の朝霧')], nextCursor: null }),
      ),
    )

    renderApp('/users/hanako')

    expect(await screen.findByRole('heading', { level: 1, name: 'はなこ' })).toBeInTheDocument()
    expect(screen.getByText('@hanako')).toBeInTheDocument()
    expect(screen.getByText('週末は温泉めぐり♨')).toBeInTheDocument()
    expect(within(counts()).getByText('28')).toBeInTheDocument()
    expect(within(counts()).getByRole('link', { name: /15 フォロー中/ })).toHaveAttribute(
      'href',
      '/users/hanako/following',
    )
    expect(within(counts()).getByRole('link', { name: /1,200 フォロワー/ })).toHaveAttribute(
      'href',
      '/users/hanako/followers',
    )
    expect(screen.getByRole('button', { name: '@hanako をフォローする' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '湯布院の朝霧' })).toBeInTheDocument()
    expect(screen.queryByText('非公開のスポットはあなたにだけ表示されています。')).not.toBeInTheDocument()
  })

  it('自分のプロフィールではフォローボタンの代わりに「プロフィール編集」と非公開の案内を出す', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/users/taro_trip/spots', () =>
        HttpResponse.json({
          items: [spotSummaryFixture(3, '自分だけのメモ', { visibility: 'PRIVATE' })],
          nextCursor: null,
        }),
      ),
    )

    renderApp(`/users/${meFixture.username}`)

    expect(await screen.findByRole('link', { name: 'プロフィール編集' })).toHaveAttribute(
      'href',
      '/settings/profile',
    )
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '自分だけのメモ' })).toBeInTheDocument()
    expect(screen.getByText('非公開のスポットはあなたにだけ表示されています。')).toBeInTheDocument()
  })

  it('存在しないユーザーは「存在しません」と表示する', async () => {
    server.use(
      http.get('/api/v1/users/nobody', () =>
        HttpResponse.json(errorResponse('USER_NOT_FOUND', 'なし'), { status: 404 }),
      ),
    )

    renderApp('/users/nobody')

    expect(await screen.findByText('@nobody というユーザーは存在しません。')).toBeInTheDocument()
  })
})

describe('フォローボタン', () => {
  it('押すとすぐに「フォロー中」に切り替わり（楽観的更新）、PUT 後にフォロワー数が増え、もう一度押すと DELETE で解除する', async () => {
    givenLoggedIn()
    givenProfile({ followerCount: 3 })
    const requests: string[] = []
    let release: () => void = () => {}
    server.use(
      http.put('/api/v1/users/hanako/follow', async () => {
        requests.push('PUT')
        // 応答を待たせて、その間に表示が切り替わっていることを確認する
        await new Promise<void>((resolve) => (release = resolve))
        return HttpResponse.json({ username: 'hanako', following: true, followerCount: 4 })
      }),
      http.delete('/api/v1/users/hanako/follow', () => {
        requests.push('DELETE')
        return HttpResponse.json({ username: 'hanako', following: false, followerCount: 3 })
      }),
    )
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォローする' }))
    // サーバーの応答前に切り替わる
    const following = screen.getByRole('button', { name: '@hanako のフォローを解除' })
    expect(following).toHaveTextContent('フォロー中')
    // カーソルを乗せたとき用の「フォロー解除」も用意している（表示の切り替えは CSS）
    expect(following).toHaveTextContent('フォロー解除')

    release()
    await waitFor(() =>
      expect(within(counts()).getByRole('link', { name: /4 フォロワー/ })).toBeInTheDocument(),
    )

    await userEvent.click(screen.getByRole('button', { name: '@hanako のフォローを解除' }))
    expect(await screen.findByRole('button', { name: '@hanako をフォローする' })).toBeInTheDocument()
    await waitFor(() =>
      expect(within(counts()).getByRole('link', { name: /3 フォロワー/ })).toBeInTheDocument(),
    )
    expect(requests).toEqual(['PUT', 'DELETE'])
  })

  it('API が失敗したら表示を元に戻し、トーストでエラーを知らせる', async () => {
    givenLoggedIn()
    givenProfile()
    server.use(
      http.put('/api/v1/users/hanako/follow', async () => {
        await delay(20)
        return HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 })
      }),
    )
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォローする' }))
    expect(screen.getByRole('button', { name: '@hanako のフォローを解除' })).toBeInTheDocument()

    expect(
      await screen.findByText('フォローを更新できませんでした。時間をおいて再度お試しください'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '@hanako をフォローする' })).toBeInTheDocument()
  })

  it('未ログインで押すとログイン画面へ移動する（API は呼ばない）', async () => {
    let called = false
    givenProfile()
    server.use(
      http.put('/api/v1/users/hanako/follow', () => {
        called = true
        return HttpResponse.json({ username: 'hanako', following: true, followerCount: 1 })
      }),
    )
    renderApp('/users/hanako')

    await userEvent.click(await screen.findByRole('button', { name: '@hanako をフォローする' }))

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
    expect(called).toBe(false)
  })
})

describe('フォロー中一覧・フォロワー一覧', () => {
  const users = [
    { id: 3, username: 'jiro', displayName: 'じろう', avatarUrl: null, followedByMe: true },
    {
      id: 1,
      username: meFixture.username,
      displayName: meFixture.displayName,
      avatarUrl: null,
      followedByMe: false,
    },
    { id: 4, username: 'saburo', displayName: 'さぶろう', avatarUrl: null, followedByMe: false },
  ]

  it('ユーザーを並べ、各行にフォローボタンを出す（自分の行には出さない）', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/users/hanako/followers', () => HttpResponse.json({ items: users, nextCursor: null })),
    )

    renderApp('/users/hanako/followers')

    const list = await screen.findByRole('list', { name: '@hanako のフォロワー' })
    const rows = within(list).getAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getByRole('link', { name: /じろう/ })).toHaveAttribute('href', '/users/jiro')
    expect(within(rows[0]).getByRole('button', { name: '@jiro のフォローを解除' })).toBeInTheDocument()
    expect(within(rows[1]).queryByRole('button')).not.toBeInTheDocument()
    expect(within(rows[2]).getByRole('button', { name: '@saburo をフォローする' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'フォロワー' })).toHaveAttribute('aria-current', 'page')
  })

  it('行のボタンでフォローすると、その行の表示が切り替わる', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/users/hanako/following', () => HttpResponse.json({ items: users, nextCursor: null })),
      http.put('/api/v1/users/saburo/follow', () =>
        HttpResponse.json({ username: 'saburo', following: true, followerCount: 1 }),
      ),
    )

    renderApp('/users/hanako/following')

    await userEvent.click(await screen.findByRole('button', { name: '@saburo をフォローする' }))

    await waitFor(() =>
      expect(screen.getByRole('button', { name: '@saburo のフォローを解除' })).toBeEnabled(),
    )
    expect(screen.getByRole('button', { name: '@jiro のフォローを解除' })).toBeInTheDocument()
  })

  it('0件なら案内を出し、「もっと見る」で続きを読み込む', async () => {
    const requested: (string | null)[] = []
    server.use(
      http.get('/api/v1/users/hanako/following', () => HttpResponse.json({ items: [], nextCursor: null })),
      http.get('/api/v1/users/hanako/followers', ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        requested.push(cursor)
        return HttpResponse.json(
          cursor ? { items: [users[2]], nextCursor: null } : { items: [users[0]], nextCursor: 'next-1' },
        )
      }),
    )

    renderApp('/users/hanako/following')
    expect(await screen.findByText('まだ誰もフォローしていません。')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: 'フォロワー' }))
    await userEvent.click(await screen.findByRole('button', { name: 'もっと見る' }))

    expect(await screen.findByRole('link', { name: /さぶろう/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /じろう/ })).toBeInTheDocument()
    expect(requested).toEqual([null, 'next-1'])
  })

  it('存在しないユーザーは「存在しません」と表示する', async () => {
    server.use(
      http.get('/api/v1/users/nobody/followers', () =>
        HttpResponse.json(errorResponse('USER_NOT_FOUND', 'なし'), { status: 404 }),
      ),
    )

    renderApp('/users/nobody/followers')

    expect(await screen.findByText('@nobody というユーザーは存在しません。')).toBeInTheDocument()
  })
})

describe('タイムライン', () => {
  it('ヘッダーから開け、フォロー中ユーザーの投稿を表示する', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/timeline', () =>
        HttpResponse.json({ items: [spotSummaryFixture(5, '中洲の屋台')], nextCursor: null }),
      ),
    )
    renderApp('/')

    await userEvent.click(await screen.findByRole('link', { name: 'タイムライン' }))

    expect(await screen.findByRole('heading', { name: 'タイムライン' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '中洲の屋台' })).toBeInTheDocument()
  })

  it('空のときは新着スポットへの導線を出す', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/timeline', () => HttpResponse.json({ items: [], nextCursor: null })))

    renderApp('/timeline')

    expect(await screen.findByText(/フォロー中のユーザーの投稿がここに表示されます。/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新着スポットを見る' })).toHaveAttribute('href', '/')
  })

  it('未ログインではログイン画面へ移動する', async () => {
    renderApp('/timeline')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})
