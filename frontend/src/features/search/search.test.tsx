import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { errorResponse, spotFixture, spotSummaryFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

/** /api/v1/spots へのリクエストのクエリを記録し、指定した結果を返す。 */
function recordSearches(
  respond: (params: URLSearchParams) => object = () => ({ items: [], nextCursor: null }),
) {
  const requests: URLSearchParams[] = []
  server.use(
    http.get('/api/v1/spots', ({ request }) => {
      const params = new URL(request.url).searchParams
      requests.push(params)
      return HttpResponse.json(respond(params))
    }),
  )
  return requests
}

function last(requests: URLSearchParams[]) {
  return Object.fromEntries(requests[requests.length - 1])
}

describe('検索画面', () => {
  it('URL の条件（スラッグ）を ID に変換して検索し、結果を表示する', async () => {
    const requests = recordSearches(() => ({
      items: [spotSummaryFixture(3, '朝の清水寺')],
      nextCursor: null,
    }))

    renderApp('/search?areas=kyoto,kansai&q=温泉&sort=popular')

    expect(await screen.findByRole('link', { name: '朝の清水寺' })).toBeInTheDocument()
    expect(last(requests)).toEqual({ limit: '20', sort: 'popular', areaIds: '11,1', q: '温泉' })
    const chips = screen.getByRole('list', { name: '選択中のエリア' })
    expect(within(chips).getByRole('button', { name: '京都 を外す' })).toBeInTheDocument()
    expect(within(chips).getByRole('button', { name: '関西全体 を外す' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'キーワード（タイトル・場所）' })).toHaveValue('温泉')
    expect(screen.getByRole('combobox', { name: '並び順' })).toHaveValue('popular')
  })

  it('ヘッダーの「探す」から開け、条件なしなら新着順で全件を検索する', async () => {
    const requests = recordSearches()
    renderApp('/')

    await userEvent.click(await screen.findByRole('link', { name: '探す' }))

    expect(await screen.findByRole('heading', { name: 'スポットを探す' })).toBeInTheDocument()
    expect(
      await screen.findByText('該当するスポットはありませんでした。条件を変えて検索してください。'),
    ).toBeInTheDocument()
    expect(last(requests)).toEqual({ limit: '20', sort: 'newest' })
  })

  it('都道府県を選ぶ・地方全体を選ぶ・チップの × で外すと、すぐに検索し直す', async () => {
    const requests = recordSearches()
    renderApp('/search')
    await screen.findByText('該当するスポットはありませんでした。条件を変えて検索してください。')

    await userEvent.click(screen.getByText('関西'))
    await userEvent.click(screen.getByRole('checkbox', { name: '京都' }))
    await waitFor(() => expect(last(requests).areaIds).toBe('11'))

    // 地方全体を選ぶと、その地方の個別の選択はまとめられ、子タグはチェック済み・変更不可になる
    await userEvent.click(screen.getByRole('checkbox', { name: '関西全体' }))
    await waitFor(() => expect(last(requests).areaIds).toBe('1'))
    expect(screen.getByRole('checkbox', { name: '大阪' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: '大阪' })).toBeDisabled()
    expect(screen.queryByRole('button', { name: '京都 を外す' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '関西全体 を外す' }))
    await waitFor(() => expect(last(requests).areaIds).toBeUndefined())
  })

  it('キーワードは「検索」で反映し、並び順は変えた時点で反映する', async () => {
    const requests = recordSearches()
    renderApp('/search')
    await screen.findByText('該当するスポットはありませんでした。条件を変えて検索してください。')

    const keyword = screen.getByRole('searchbox', { name: 'キーワード（タイトル・場所）' })
    await userEvent.type(keyword, ' 温泉 ')
    expect(last(requests).q).toBeUndefined()
    await userEvent.click(screen.getByRole('button', { name: '検索' }))
    await waitFor(() => expect(last(requests).q).toBe('温泉'))

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '並び順' }), 'popular')
    await waitFor(() => expect(last(requests)).toMatchObject({ q: '温泉', sort: 'popular' }))
  })

  it('存在しないスラッグは無視して検索する', async () => {
    const requests = recordSearches()

    renderApp('/search?areas=atlantis,okinawa')

    await waitFor(() => expect(last(requests).areaIds).toBe('23'))
    expect(screen.queryByRole('button', { name: /atlantis/ })).not.toBeInTheDocument()
  })

  it('人気順でページをめくる間に順位が動いても、同じスポットは1回だけ表示する', async () => {
    recordSearches((params) =>
      params.get('cursor')
        ? { items: [spotSummaryFixture(2, 'B'), spotSummaryFixture(3, 'C')], nextCursor: null }
        : { items: [spotSummaryFixture(1, 'A'), spotSummaryFixture(2, 'B')], nextCursor: 'next' },
    )
    renderApp('/search?sort=popular')

    await userEvent.click(await screen.findByRole('button', { name: 'もっと見る' }))

    expect(await screen.findByRole('link', { name: 'C' })).toBeInTheDocument()
    const results = screen.getByRole('list', { name: '検索結果' })
    expect(
      within(results)
        .getAllByRole('heading')
        .map((h) => h.textContent),
    ).toEqual(['A', 'B', 'C'])
  })

  it('検索に失敗したら再読み込みできる', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json(errorResponse('INTERNAL_ERROR', 'エラー'), { status: 500 }),
      ),
    )
    renderApp('/search?q=温泉')

    expect(await screen.findByText('スポットを読み込めませんでした。')).toBeInTheDocument()
  })
})

describe('エリアタグから検索', () => {
  it('スポット詳細のエリアタグを押すと、そのタグで検索した結果を表示する', async () => {
    const requests = recordSearches()
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))
    renderApp('/spots/100')

    await userEvent.click(await screen.findByRole('link', { name: '京都のスポットを探す' }))

    expect(await screen.findByRole('heading', { name: 'スポットを探す' })).toBeInTheDocument()
    await waitFor(() => expect(last(requests).areaIds).toBe('11'))
  })
})
