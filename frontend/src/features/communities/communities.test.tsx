import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, meFixture, otherUser } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'
import { parseCommunitySearchParams, toCommunitySearchParams } from './searchParams'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

const me = {
  id: meFixture.id,
  username: meFixture.username,
  displayName: meFixture.displayName,
  avatarUrl: null,
  badge: null,
}

function communityFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 3,
    name: '北陸・富山のお寺巡り同好会',
    description: '瑞龍寺や勝興寺など、北陸のお寺の情報を交換しましょう。\n初めての方も歓迎です。',
    categories: ['REGIONAL', 'BEGINNER_FRIENDLY'],
    owner: otherUser,
    memberCount: 4,
    topicCount: 1,
    joined: false,
    createdAt: '2026-11-19T03:00:00Z',
    ...overrides,
  }
}

function communitySummary(id: number, name: string, overrides: Record<string, unknown> = {}) {
  const { description, topicCount: _topicCount, ...rest } = communityFixture({ id, name, ...overrides })
  return { ...rest, descriptionExcerpt: description }
}

function topicFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 50,
    title: '雪の瑞龍寺の撮影スポット',
    body: '回廊の正面からがおすすめです。',
    author: otherUser,
    replyCount: 0,
    lastActivityAt: '2026-11-19T04:00:00Z',
    createdAt: '2026-11-19T04:00:00Z',
    community: { id: 3, name: '北陸・富山のお寺巡り同好会', ownerId: otherUser.id },
    ...overrides,
  }
}

/** 詳細画面で使う API（詳細・メンバー・トピック）。 */
function givenCommunity(overrides: Record<string, unknown> = {}) {
  const community = communityFixture(overrides)
  server.use(
    http.get('/api/v1/communities/3', () => HttpResponse.json(community)),
    http.get('/api/v1/communities/3/members', () =>
      HttpResponse.json({
        items: [
          { user: community.owner, owner: true, joinedAt: '2026-11-19T03:00:00Z' },
          {
            user: {
              id: 7,
              username: 'kenta',
              displayName: 'カメラ好きのケンタ',
              avatarUrl: null,
              badge: null,
            },
            owner: false,
            joinedAt: '2026-11-19T05:00:00Z',
          },
        ],
        nextCursor: null,
      }),
    ),
    http.get('/api/v1/communities/3/topics', () => {
      const { body, ...rest } = topicFixture()
      return HttpResponse.json({ items: [{ ...rest, bodyExcerpt: body }], nextCursor: null })
    }),
  )
  return community
}

describe('コミュニティ一覧', () => {
  it('ヘッダーのリンクから開け、カテゴリ・メンバー数・作成者・参加中を表示する', async () => {
    server.use(
      http.get('/api/v1/communities', () =>
        HttpResponse.json({
          items: [
            communitySummary(3, '北陸・富山のお寺巡り同好会', { joined: true }),
            communitySummary(4, '限定御朱印情報シェアの会', { categories: ['GOSHUIN'], memberCount: 2 }),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    await userEvent.click(await screen.findByRole('link', { name: 'コミュニティ' }))

    expect(await screen.findByRole('heading', { name: 'コミュニティ', level: 1 })).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'コミュニティ' })
    const toyama = within(list).getByRole('article', { name: '北陸・富山のお寺巡り同好会' })
    expect(within(toyama).getByRole('link', { name: '北陸・富山のお寺巡り同好会' })).toHaveAttribute(
      'href',
      '/communities/3',
    )
    expect(within(toyama).getByText('参加中')).toBeInTheDocument()
    expect(within(toyama).getByText('地域別')).toBeInTheDocument()
    expect(within(toyama).getByText('初心者歓迎')).toBeInTheDocument()
    expect(within(toyama).getByText('メンバー 4人')).toBeInTheDocument()
    expect(within(toyama).getByRole('link', { name: 'はなこ' })).toBeInTheDocument()
    const goshuin = within(list).getByRole('article', { name: '限定御朱印情報シェアの会' })
    expect(within(goshuin).queryByText('参加中')).not.toBeInTheDocument()
    expect(within(goshuin).getByText('メンバー 2人')).toBeInTheDocument()
  })

  it('キーワード・カテゴリ・並び順を変えると、その条件で検索し直す', async () => {
    const requests: URLSearchParams[] = []
    server.use(
      http.get('/api/v1/communities', ({ request }) => {
        requests.push(new URL(request.url).searchParams)
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderApp('/communities')

    await userEvent.type(await screen.findByLabelText('キーワード（名前・説明文）'), '富山')
    await userEvent.click(screen.getByRole('button', { name: '検索' }))
    await waitFor(() => expect(requests.at(-1)?.get('q')).toBe('富山'))

    await userEvent.click(screen.getByRole('button', { name: '御朱印' }))
    await waitFor(() => expect(requests.at(-1)?.get('category')).toBe('GOSHUIN'))
    expect(screen.getByRole('button', { name: '御朱印' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.selectOptions(screen.getByLabelText('並び順'), 'popular')
    await waitFor(() => expect(requests.at(-1)?.get('sort')).toBe('popular'))
    expect(requests.at(-1)?.get('q')).toBe('富山')
    expect(requests.at(-1)?.get('category')).toBe('GOSHUIN')
    expect(await screen.findByText(/該当するコミュニティはありませんでした/)).toBeInTheDocument()

    // 選択中のカテゴリをもう一度押すと解除する
    await userEvent.click(screen.getByRole('button', { name: '御朱印' }))
    await waitFor(() => expect(requests.at(-1)?.get('category')).toBeNull())
  })

  it('未ログインなら作成ボタンの代わりにログインへの案内を出す', async () => {
    server.use(http.get('/api/v1/communities', () => HttpResponse.json({ items: [], nextCursor: null })))
    renderApp('/communities')

    expect(await screen.findByRole('link', { name: 'ログインしてコミュニティを作る' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcommunities%2Fnew',
    )
    expect(await screen.findByText(/まだコミュニティはありません/)).toBeInTheDocument()
  })
})

describe('URL のクエリと検索条件', () => {
  it('不正な値は無視し、既定値は URL に出さない', () => {
    expect(parseCommunitySearchParams(new URLSearchParams('q=%20富山%20&category=UNKNOWN&sort=x'))).toEqual({
      q: '富山',
      category: null,
      sort: 'newest',
    })
    expect(toCommunitySearchParams({ q: '', category: null, sort: 'newest' }).toString()).toBe('')
    expect(
      toCommunitySearchParams({ q: '庭園', category: 'ARCHITECTURE_GARDEN', sort: 'popular' }).toString(),
    ).toBe('q=%E5%BA%AD%E5%9C%92&category=ARCHITECTURE_GARDEN&sort=popular')
  })
})

describe('コミュニティの作成', () => {
  it('入力して作成すると、作成したコミュニティの画面へ移動する', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    givenCommunity({ owner: me, joined: true, memberCount: 1, topicCount: 0 })
    server.use(
      http.get('/api/v1/communities', () => HttpResponse.json({ items: [], nextCursor: null })),
      http.post('/api/v1/communities', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(communityFixture({ owner: me, joined: true, memberCount: 1 }), {
          status: 201,
        })
      }),
    )
    renderApp('/communities')

    await userEvent.click(await screen.findByRole('link', { name: 'コミュニティを作る' }))
    await userEvent.type(await screen.findByLabelText('コミュニティ名'), '北陸・富山のお寺巡り同好会')
    await userEvent.type(screen.getByLabelText('説明文（テーマ・目的）'), '北陸のお寺の情報を交換しましょう')
    await userEvent.click(screen.getByRole('checkbox', { name: '初心者歓迎' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '地域別' }))
    await userEvent.click(screen.getByRole('button', { name: 'コミュニティを作成する' }))

    expect(await screen.findByRole('heading', { name: '北陸・富山のお寺巡り同好会' })).toBeInTheDocument()
    expect(sentBody).toEqual({
      name: '北陸・富山のお寺巡り同好会',
      description: '北陸のお寺の情報を交換しましょう',
      // 表示順に並べて送る
      categories: ['REGIONAL', 'BEGINNER_FRIENDLY'],
    })
  })

  it('未入力なら送信前にエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/communities/new')

    await userEvent.click(await screen.findByRole('button', { name: 'コミュニティを作成する' }))

    expect(await screen.findByText('コミュニティ名を入力してください')).toBeInTheDocument()
    expect(screen.getByText('説明文を入力してください')).toBeInTheDocument()
    expect(screen.getByText('カテゴリを1つ以上選んでください')).toBeInTheDocument()
  })

  it('同じ名前があるとき（409）は名前の欄にエラーを出す', async () => {
    givenLoggedIn()
    server.use(
      http.post('/api/v1/communities', () =>
        HttpResponse.json(errorResponse('COMMUNITY_NAME_TAKEN', '同じ名前のコミュニティがすでにあります'), {
          status: 409,
        }),
      ),
    )
    renderApp('/communities/new')

    await userEvent.type(await screen.findByLabelText('コミュニティ名'), '限定御朱印情報シェアの会')
    await userEvent.type(screen.getByLabelText('説明文（テーマ・目的）'), '説明')
    await userEvent.click(screen.getByRole('checkbox', { name: '御朱印' }))
    await userEvent.click(screen.getByRole('button', { name: 'コミュニティを作成する' }))

    expect(await screen.findByText('同じ名前のコミュニティがすでにあります')).toBeInTheDocument()
    expect(screen.getByLabelText('コミュニティ名')).toHaveAttribute('aria-invalid', 'true')
  })

  it('未ログインで作成画面を開くとログインへ移動する', async () => {
    renderApp('/communities/new')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })
})

describe('コミュニティの詳細', () => {
  it('説明（改行を保持）・オーナー・メンバー数・メンバー一覧・トピックを表示する。未ログインは参加をログインへ案内する', async () => {
    givenCommunity()
    renderApp('/communities/3')

    expect(await screen.findByRole('heading', { name: '北陸・富山のお寺巡り同好会' })).toBeInTheDocument()
    expect(screen.getByText(/初めての方も歓迎です。/)).toBeInTheDocument()
    expect(screen.getByText('4人')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'メンバー（4人）' })).toBeInTheDocument()
    const members = await screen.findByRole('list', { name: 'メンバー' })
    expect(within(members).getAllByRole('listitem')[0]).toHaveTextContent('はなこ')
    expect(within(members).getAllByRole('listitem')[0]).toHaveTextContent('オーナー')
    expect(within(members).getByText('カメラ好きのケンタ')).toBeInTheDocument()
    const topics = await screen.findByRole('list', { name: 'トピック' })
    expect(within(topics).getByRole('link', { name: '雪の瑞龍寺の撮影スポット' })).toHaveAttribute(
      'href',
      '/community/topics/50',
    )
    expect(screen.getByRole('link', { name: 'ログインして参加する' })).toHaveAttribute(
      'href',
      '/login?redirect=%2Fcommunities%2F3',
    )
    expect(screen.queryByRole('button', { name: 'トピックを作成する' })).not.toBeInTheDocument()
  })

  it('参加するとメンバー数が増えてトピックを作れるようになり、退出すると元に戻る', async () => {
    givenLoggedIn()
    givenCommunity()
    const calls: string[] = []
    server.use(
      http.put('/api/v1/communities/3/membership', () => {
        calls.push('PUT')
        return HttpResponse.json({ communityId: 3, joined: true, memberCount: 5 })
      }),
      http.delete('/api/v1/communities/3/membership', () => {
        calls.push('DELETE')
        return HttpResponse.json({ communityId: 3, joined: false, memberCount: 4 })
      }),
    )
    renderApp('/communities/3')

    expect(
      await screen.findByText(/コミュニティに参加すると、トピックの作成や返信ができます/),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'コミュニティに参加する' }))

    expect(await screen.findByRole('button', { name: '退出する' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'メンバー（5人）' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'トピックを作成する' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '退出する' }))
    expect(await screen.findByRole('button', { name: 'コミュニティに参加する' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'メンバー（4人）' })).toBeInTheDocument()
    expect(calls).toEqual(['PUT', 'DELETE'])
  })

  it('画面を開き直しても、ログイン状態を復元してから参加状態を読む', async () => {
    givenLoggedIn()
    givenCommunity()
    // サーバーはトークン付きのリクエストにだけ joined: true を返す
    server.use(
      http.get('/api/v1/communities/3', ({ request }) =>
        HttpResponse.json(communityFixture({ joined: request.headers.has('Authorization') })),
      ),
    )
    renderApp('/communities/3')

    expect(await screen.findByRole('button', { name: '退出する' })).toBeInTheDocument()
    expect(screen.getByText('参加中')).toBeInTheDocument()
  })

  it('メンバーはトピックを作成でき、作成するとトピックの画面へ移動する', async () => {
    givenLoggedIn()
    givenCommunity({ joined: true })
    let sentBody: unknown = null
    server.use(
      http.post('/api/v1/communities/3/topics', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(topicFixture({ id: 60, title: '勝興寺の拝観時間', author: me }), {
          status: 201,
        })
      }),
      http.get('/api/v1/board-topics/60', () =>
        HttpResponse.json(topicFixture({ id: 60, title: '勝興寺の拝観時間', author: me })),
      ),
    )
    renderApp('/communities/3')

    await userEvent.type(await screen.findByLabelText('タイトル'), '勝興寺の拝観時間')
    await userEvent.type(screen.getByLabelText('本文'), '冬の拝観時間を知っている方いますか？')
    await userEvent.click(screen.getByRole('button', { name: 'トピックを作成する' }))

    expect(await screen.findByRole('heading', { name: '勝興寺の拝観時間' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '← 北陸・富山のお寺巡り同好会' })).toHaveAttribute(
      'href',
      '/communities/3',
    )
    expect(sentBody).toEqual({ title: '勝興寺の拝観時間', body: '冬の拝観時間を知っている方いますか？' })
  })

  it('オーナーには参加ボタンの代わりに削除ボタンを出し、削除すると一覧へ戻る', async () => {
    givenLoggedIn()
    givenCommunity({ owner: me, joined: true })
    let deleted = false
    server.use(
      http.get('/api/v1/communities', () => HttpResponse.json({ items: [], nextCursor: null })),
      http.delete('/api/v1/communities/3', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/communities/3')

    await screen.findByRole('heading', { name: '北陸・富山のお寺巡り同好会' })
    expect(screen.queryByRole('button', { name: '退出する' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'コミュニティを削除' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '削除する' }))

    expect(await screen.findByRole('heading', { name: 'コミュニティ', level: 1 })).toBeInTheDocument()
    expect(deleted).toBe(true)
  })

  it('存在しないコミュニティは「見つかりません」と表示する', async () => {
    server.use(
      http.get('/api/v1/communities/99', () =>
        HttpResponse.json(errorResponse('COMMUNITY_NOT_FOUND', 'なし'), { status: 404 }),
      ),
    )
    renderApp('/communities/99')

    expect(await screen.findByRole('heading', { name: 'コミュニティが見つかりません' })).toBeInTheDocument()
  })
})

describe('コミュニティ内のトピック', () => {
  function givenTopicWithReplies() {
    server.use(
      http.get('/api/v1/board-topics/50', () => HttpResponse.json(topicFixture({ replyCount: 1 }))),
      http.get('/api/v1/board-topics/50/replies', () =>
        HttpResponse.json({
          items: [
            {
              id: 1,
              topicId: 50,
              body: '朝8時台が空いていました',
              author: {
                id: 7,
                username: 'kenta',
                displayName: 'カメラ好きのケンタ',
                avatarUrl: null,
                badge: null,
              },
              createdAt: '2026-11-19T05:00:00Z',
            },
          ],
          nextCursor: null,
        }),
      ),
    )
  }

  it('メンバーでなければ返信フォームの代わりに参加への案内を出す', async () => {
    givenLoggedIn()
    givenTopicWithReplies()
    givenCommunity({ joined: false })
    renderApp('/community/topics/50')

    expect(await screen.findByText(/返信できるのはコミュニティのメンバーだけです/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '「北陸・富山のお寺巡り同好会」に参加する' })).toHaveAttribute(
      'href',
      '/communities/3',
    )
    expect(screen.queryByLabelText('返信を書く')).not.toBeInTheDocument()
  })

  it('メンバーは返信できる', async () => {
    givenLoggedIn()
    givenTopicWithReplies()
    givenCommunity({ joined: true })
    renderApp('/community/topics/50')

    expect(await screen.findByLabelText('返信を書く')).toBeInTheDocument()
  })

  it('コミュニティのオーナーは他人のトピック・返信を削除できる', async () => {
    givenLoggedIn()
    givenTopicWithReplies()
    givenCommunity({ owner: me, joined: true })
    server.use(
      http.get('/api/v1/board-topics/50', () =>
        HttpResponse.json(
          topicFixture({
            replyCount: 1,
            community: { id: 3, name: '北陸・富山のお寺巡り同好会', ownerId: me.id },
          }),
        ),
      ),
    )
    renderApp('/community/topics/50')

    const reply = await screen.findByRole('article', { name: 'カメラ好きのケンタの返信' })
    await userEvent.click(within(reply).getByRole('button', { name: '削除' }))
    expect(
      within(screen.getByRole('alertdialog')).getByText(/コミュニティのオーナーとして削除します/),
    ).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'キャンセル' }))
    // トピックの削除ボタンと返信の削除ボタン
    expect(screen.getAllByRole('button', { name: '削除' })).toHaveLength(2)
  })
})
