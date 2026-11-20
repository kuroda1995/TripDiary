import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, spotFixture, spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'
import { averageRating } from './temple'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

function ratingGroup(name: string) {
  return screen.getByRole('radiogroup', { name })
}

describe('お寺の評価・特徴の入力', () => {
  it('評価（一部の項目だけ）と特徴タグを選んで投稿すると、表示順にそろえて送る', async () => {
    givenLoggedIn()
    let sentBody: Record<string, unknown> | null = null
    server.use(
      http.post('/api/v1/spots', async ({ request }) => {
        sentBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(spotFixture({ id: 555, title: '瑞龍寺' }), { status: 201 })
      }),
    )
    renderApp('/spots/new')

    await userEvent.type(await screen.findByLabelText('タイトル'), '瑞龍寺')
    await userEvent.type(screen.getByLabelText('場所'), '高岡市')
    await userEvent.click(await screen.findByText('関西'))
    await userEvent.click(screen.getByRole('checkbox', { name: '京都' }))
    await userEvent.click(within(ratingGroup('静寂さ・雰囲気')).getByRole('radio', { name: '5点' }))
    await userEvent.click(within(ratingGroup('庭園・建築の美しさ')).getByRole('radio', { name: '4点' }))
    // 一度付けた評価は「未評価」で外せる
    await userEvent.click(
      within(ratingGroup('アクセス・駐車場の停めやすさ')).getByRole('radio', { name: '2点' }),
    )
    await userEvent.click(
      within(ratingGroup('アクセス・駐車場の停めやすさ')).getByRole('radio', { name: '未評価' }),
    )
    await userEvent.click(screen.getByRole('checkbox', { name: '静かに過ごせる' }))
    await userEvent.click(screen.getByRole('checkbox', { name: '御朱印あり' }))
    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    await screen.findByRole('heading', { name: '瑞龍寺' })
    expect(sentBody).toMatchObject({
      ratings: { tranquility: 5, beauty: 4, worship: null, access: null },
      templeFeatures: ['GOSHUIN', 'QUIET'],
    })
  })

  it('編集画面では保存済みの評価・特徴が選ばれた状態で開く', async () => {
    givenLoggedIn()
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(
          spotFixture({
            ratings: { tranquility: 3, beauty: null, worship: 5, access: null },
            templeFeatures: ['PARKING'],
          }),
        ),
      ),
    )
    renderApp('/spots/100/edit')

    await screen.findByLabelText('タイトル')
    expect(within(ratingGroup('静寂さ・雰囲気')).getByRole('radio', { name: '3点' })).toBeChecked()
    expect(within(ratingGroup('庭園・建築の美しさ')).getByRole('radio', { name: '未評価' })).toBeChecked()
    expect(within(ratingGroup('御朱印・参拝の充実度')).getByRole('radio', { name: '5点' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: '駐車場あり' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: '御朱印あり' })).not.toBeChecked()
  })
})

describe('お寺の評価・特徴の表示', () => {
  it('詳細画面に評価した項目と特徴タグを表示し、タグから検索できる', async () => {
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(
          spotFixture({
            ratings: { tranquility: 5, beauty: 4, worship: null, access: null },
            templeFeatures: ['GOSHUIN', 'CULTURAL_PROPERTY'],
          }),
        ),
      ),
    )
    renderApp('/spots/100')

    const section = await screen.findByRole('region', { name: 'お寺の評価' })
    expect(within(section).getByText('静寂さ・雰囲気')).toBeInTheDocument()
    expect(within(section).getByRole('img', { name: '5段階中5' })).toBeInTheDocument()
    expect(within(section).getByRole('img', { name: '5段階中4' })).toBeInTheDocument()
    // 未評価の項目は出さない
    expect(within(section).queryByText('御朱印・参拝の充実度')).not.toBeInTheDocument()

    const features = screen.getByRole('list', { name: 'お寺の特徴' })
    expect(within(features).getByRole('link', { name: '「御朱印あり」のお寺を探す' })).toHaveAttribute(
      'href',
      '/search?features=GOSHUIN',
    )
    expect(within(features).getByText('国宝・重要文化財')).toBeInTheDocument()
  })

  it('評価のない投稿（お寺以外の旅行記録）には評価欄を出さない', async () => {
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))
    renderApp('/spots/100')

    await screen.findByRole('heading', { name: '朝の清水寺' })
    expect(screen.queryByRole('region', { name: 'お寺の評価' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'お寺の特徴' })).not.toBeInTheDocument()
  })

  it('一覧カードに評価の平均と特徴タグを表示する', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [
            spotSummaryFixture(1, '瑞龍寺', {
              ratings: { tranquility: 5, beauty: 4, worship: 4, access: null },
              templeFeatures: ['GARDEN'],
            }),
          ],
          nextCursor: null,
        }),
      ),
    )
    renderApp('/')

    const list = await screen.findByRole('list', { name: '新着スポット' })
    expect(within(list).getByText('4.3')).toBeInTheDocument()
    expect(within(list).getByText('庭園が美しい')).toBeInTheDocument()
  })
})

describe('お寺の特徴での絞り込み', () => {
  it('検索画面で特徴を選ぶとすぐに検索し直し、URL にも残る', async () => {
    const requests: URLSearchParams[] = []
    server.use(
      http.get('/api/v1/spots', ({ request }) => {
        requests.push(new URL(request.url).searchParams)
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )
    renderApp('/search?features=GOSHUIN')

    expect(await screen.findByRole('checkbox', { name: '御朱印あり' })).toBeChecked()
    await waitFor(() => expect(requests.at(-1)?.get('features')).toBe('GOSHUIN'))

    await userEvent.click(screen.getByRole('checkbox', { name: '庭園が美しい' }))
    await waitFor(() => expect(requests.at(-1)?.get('features')).toBe('GOSHUIN,GARDEN'))

    await userEvent.click(screen.getByRole('checkbox', { name: '御朱印あり' }))
    await waitFor(() => expect(requests.at(-1)?.get('features')).toBe('GARDEN'))
  })
})

describe('averageRating', () => {
  it('評価した項目だけの平均を小数第1位で返し、評価がなければ null', () => {
    expect(averageRating({ tranquility: 5, beauty: 4, worship: 4, access: null })).toBe(4.3)
    expect(averageRating({ tranquility: null, beauty: null, worship: null, access: null })).toBeNull()
    expect(averageRating(null)).toBeNull()
  })
})
