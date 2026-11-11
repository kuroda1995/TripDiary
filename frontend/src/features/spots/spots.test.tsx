import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, otherUser, spotFixture, spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

describe('新着一覧（トップページ）', () => {
  it('新着スポットをカードで表示し、「もっと見る」で次のページを読み込む', async () => {
    const cursors: (string | null)[] = []
    server.use(
      http.get('/api/v1/spots', ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        cursors.push(cursor)
        return cursor === null
          ? HttpResponse.json({ items: [spotSummaryFixture(2, '新しいスポット')], nextCursor: 'c1' })
          : HttpResponse.json({
              items: [spotSummaryFixture(1, '古いスポット', { visibility: 'PUBLIC' })],
              nextCursor: null,
            })
      }),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    expect(within(list).getByRole('link', { name: '新しいスポット' })).toHaveAttribute('href', '/spots/2')
    expect(within(list).getByText('京都')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'もっと見る' }))

    expect(await screen.findByRole('link', { name: '古いスポット' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'もっと見る' })).not.toBeInTheDocument()
    expect(cursors).toEqual([null, 'c1'])
  })

  it('投稿が無いときはメッセージを表示する', async () => {
    renderApp('/')

    expect(await screen.findByText(/まだ投稿はありません/)).toBeInTheDocument()
  })
})

describe('スポット詳細', () => {
  it('内容を表示し、投稿者本人には編集・削除ボタンを表示する', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))

    renderApp('/spots/100')

    expect(await screen.findByRole('heading', { name: '朝の清水寺' })).toBeInTheDocument()
    expect(screen.getByText('開門直後に行くと人が少なくて最高でした。')).toBeInTheDocument()
    expect(screen.getByText('2026年10月3日')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '編集' })).toHaveAttribute('href', '/spots/100/edit')
    expect(screen.getByRole('button', { name: '削除' })).toBeInTheDocument()
  })

  it('他人のスポットには編集・削除ボタンを表示しない', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ author: otherUser }))))

    renderApp('/spots/100')

    expect(await screen.findByRole('heading', { name: '朝の清水寺' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '編集' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '削除' })).not.toBeInTheDocument()
  })

  it('存在しない・非公開のスポットは「見つかりません」と表示する', async () => {
    server.use(
      http.get('/api/v1/spots/999', () =>
        HttpResponse.json(errorResponse('SPOT_NOT_FOUND', 'なし'), { status: 404 }),
      ),
    )

    renderApp('/spots/999')

    expect(await screen.findByRole('heading', { name: 'スポットが見つかりません' })).toBeInTheDocument()
  })

  it('不正な ID の URL は API を呼ばずに「見つかりません」と表示する', async () => {
    renderApp('/spots/abc')

    expect(await screen.findByRole('heading', { name: 'スポットが見つかりません' })).toBeInTheDocument()
  })

  it('再読み込み直後でも、ログイン状態の復元を待ってからトークン付きで取得する（本人の非公開スポット）', async () => {
    let authorization: string | null = null
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        await delay(50)
        return HttpResponse.json(authResponse())
      }),
      http.get('/api/v1/spots/100', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return authorization
          ? HttpResponse.json(spotFixture({ visibility: 'PRIVATE' }))
          : HttpResponse.json(errorResponse('SPOT_NOT_FOUND', 'なし'), { status: 404 })
      }),
    )

    renderApp('/spots/100')

    expect(await screen.findByRole('heading', { name: '朝の清水寺' })).toBeInTheDocument()
    expect(screen.getByText('非公開')).toBeInTheDocument()
    expect(authorization).toBe('Bearer access-token-1')
  })

  it('確認ダイアログで削除すると、投稿者のページへ移動する', async () => {
    givenLoggedIn()
    let deleted = false
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())),
      http.delete('/api/v1/spots/100', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
      http.get('/api/v1/users/taro_trip/spots', () => HttpResponse.json({ items: [], nextCursor: null })),
    )
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: '削除' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText(/「朝の清水寺」を削除します/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: '削除する' }))

    // 投稿者（本人）のプロフィール
    expect(await screen.findByRole('heading', { level: 1, name: '旅するタロウ' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'プロフィール編集' })).toBeInTheDocument()
    expect(deleted).toBe(true)
  })

  it('削除の確認でキャンセルすると削除しない', async () => {
    givenLoggedIn()
    let deleted = false
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())),
      http.delete('/api/v1/spots/100', () => {
        deleted = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('button', { name: '削除' }))
    await userEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'キャンセル' }),
    )

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(deleted).toBe(false)
  })
})

describe('スポット投稿', () => {
  async function openRegionAndCheck(region: string, tag: string) {
    // エリアタグ一覧の読み込みを待つ
    await userEvent.click(await screen.findByText(region))
    await userEvent.click(screen.getByRole('checkbox', { name: tag }))
  }

  it('未ログインで開くとログイン画面へ移動する', async () => {
    renderApp('/spots/new')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('必須項目が未入力なら送信前にエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/spots/new')

    await userEvent.click(await screen.findByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('タイトルを入力してください')).toBeInTheDocument()
    expect(screen.getByText('場所を入力してください')).toBeInTheDocument()
    expect(screen.getByText('エリアタグを1つ以上選択してください')).toBeInTheDocument()
  })

  it('入力して投稿すると、作成したスポットの詳細画面へ移動する', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    server.use(
      http.post('/api/v1/spots', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(spotFixture({ id: 555, title: '中華街の夜' }), { status: 201 })
      }),
    )
    renderApp('/spots/new')

    await userEvent.type(await screen.findByLabelText('タイトル'), '中華街の夜')
    await userEvent.type(screen.getByLabelText('場所'), '長崎新地中華街')
    await openRegionAndCheck('九州・沖縄', '長崎')
    await userEvent.type(screen.getByLabelText('感想（任意）'), 'ちゃんぽんが絶品')
    await userEvent.click(screen.getByLabelText('非公開（自分だけ）'))
    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByRole('heading', { name: '中華街の夜' })).toBeInTheDocument()
    expect(sentBody).toEqual({
      title: '中華街の夜',
      locationName: '長崎新地中華街',
      areaTagIds: [22],
      impression: 'ちゃんぽんが絶品',
      visitedOn: null,
      visibility: 'PRIVATE',
      photos: [],
    })
  })

  it('エリアタグは5個まで。5個選ぶと残りは選べず、チップから外せる', async () => {
    givenLoggedIn()
    renderApp('/spots/new')
    await screen.findByLabelText('タイトル')

    await openRegionAndCheck('関西', '京都')
    await userEvent.click(screen.getByRole('checkbox', { name: '大阪' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '兵庫' }))
    await openRegionAndCheck('九州・沖縄', '福岡')
    await userEvent.click(screen.getByRole('checkbox', { name: '長崎' }))

    expect(screen.getByRole('checkbox', { name: '沖縄' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '京都 を外す' }))

    expect(screen.getByRole('checkbox', { name: '京都' })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: '沖縄' })).toBeEnabled()
  })

  it('サーバーのエラー（存在しないエリアタグなど）は該当項目に表示する', async () => {
    givenLoggedIn()
    server.use(
      http.post('/api/v1/spots', () =>
        HttpResponse.json(errorResponse('AREA_TAG_NOT_FOUND', '存在しないエリアタグが指定されています'), {
          status: 400,
        }),
      ),
    )
    renderApp('/spots/new')

    await userEvent.type(await screen.findByLabelText('タイトル'), 't')
    await userEvent.type(screen.getByLabelText('場所'), 'l')
    await openRegionAndCheck('関西', '京都')
    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('存在しないエリアタグが指定されています')).toBeInTheDocument()
  })
})

describe('スポット編集', () => {
  it('既存の内容が入った状態で開き、保存すると PUT して詳細画面へ戻る', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    server.use(
      http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())),
      http.put('/api/v1/spots/100', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(spotFixture({ title: '夕方の清水寺' }))
      }),
    )
    renderApp('/spots/100/edit')

    const title = await screen.findByLabelText('タイトル')
    expect(title).toHaveValue('朝の清水寺')
    expect(await screen.findByRole('button', { name: '京都 を外す' })).toBeInTheDocument()

    await userEvent.clear(title)
    await userEvent.type(title, '夕方の清水寺')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByRole('heading', { name: '夕方の清水寺' })).toBeInTheDocument()
    expect(sentBody).toMatchObject({ title: '夕方の清水寺', areaTagIds: [11], visitedOn: '2026-10-03' })
  })

  it('他人のスポットの編集画面は「編集できません」と表示する', async () => {
    givenLoggedIn()
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture({ author: otherUser }))))

    renderApp('/spots/100/edit')

    expect(await screen.findByRole('heading', { name: '編集できません' })).toBeInTheDocument()
  })
})
