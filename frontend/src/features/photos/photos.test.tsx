import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  authResponse,
  errorResponse,
  spotFixture,
  spotSummaryFixture,
  storagePutHandler,
  uploadHandlers,
} from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn() {
  server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())))
}

function jpeg(name: string) {
  return new File([new Uint8Array(100)], name, { type: 'image/jpeg' })
}

async function fillRequiredFields() {
  await userEvent.type(await screen.findByLabelText('タイトル'), '朝の清水寺')
  await userEvent.type(screen.getByLabelText('場所'), '清水寺')
  await userEvent.click(await screen.findByText('関西'))
  await userEvent.click(screen.getByRole('checkbox', { name: '京都' }))
}

function photoList() {
  return screen.getByRole('list', { name: '選択した写真' })
}

describe('スポット投稿の写真', () => {
  it('選んだ写真を S3 へ直接アップロードし、並べ替えた順で投稿する（先頭がカバー）', async () => {
    givenLoggedIn()
    const puts: { key: string; contentType: string | null }[] = []
    let sentBody: unknown = null
    server.use(
      ...uploadHandlers((key, contentType) => puts.push({ key, contentType })),
      http.post('/api/v1/spots', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(spotFixture({ id: 555 }), { status: 201 })
      }),
    )
    renderApp('/spots/new')
    await fillRequiredFields()

    await userEvent.upload(screen.getByLabelText('写真を追加'), [jpeg('a.jpg'), jpeg('b.jpg')])

    // アップロードが終わるとサムネイルが並び、先頭にカバーの印が付く
    await waitFor(() => expect(within(photoList()).getAllByRole('img')).toHaveLength(2))
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())
    expect(within(photoList()).getAllByRole('listitem')[0]).toHaveTextContent('カバー')
    expect(puts.map((p) => p.contentType)).toEqual(['image/jpeg', 'image/jpeg'])

    // 2枚目を先頭へ
    await userEvent.click(screen.getByRole('button', { name: '写真2を前へ移動' }))
    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    await waitFor(() => expect(sentBody).not.toBeNull())
    const keys = puts.map((p) => p.key)
    expect(sentBody).toMatchObject({
      photos: [
        { s3Key: keys[1], sortOrder: 0 },
        { s3Key: keys[0], sortOrder: 1 },
      ],
    })
  })

  it('アップロード中は投稿ボタンを押せない', async () => {
    givenLoggedIn()
    server.use(...uploadHandlers())
    server.use(
      storagePutHandler(async () => {
        await delay('infinite')
        return 200
      }),
    )
    renderApp('/spots/new')
    await fillRequiredFields()

    await userEvent.upload(screen.getByLabelText('写真を追加'), jpeg('a.jpg'))

    expect(await screen.findByRole('button', { name: '写真をアップロード中…' })).toBeDisabled()
    expect(await screen.findByRole('progressbar', { name: '写真1のアップロード' })).toBeInTheDocument()
  })

  it('アップロードに失敗した写真は再試行でき、失敗したままでは投稿できない', async () => {
    givenLoggedIn()
    let attempts = 0
    server.use(...uploadHandlers())
    server.use(
      storagePutHandler(() => {
        attempts += 1
        return attempts === 1 ? 500 : 200
      }),
    )
    renderApp('/spots/new')
    await fillRequiredFields()

    await userEvent.upload(screen.getByLabelText('写真を追加'), jpeg('a.jpg'))
    expect(await screen.findByText('アップロードに失敗しました')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))
    expect(
      await screen.findByText('アップロードできなかった写真があります。再試行するか削除してください'),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '再試行' }))

    // 再試行が成功すると投稿できる状態に戻る
    expect(await screen.findByRole('button', { name: '投稿する' })).toBeEnabled()
    await waitFor(() => expect(attempts).toBe(2))
    expect(screen.queryByText('アップロードに失敗しました')).not.toBeInTheDocument()
    expect(within(photoList()).getByRole('img', { name: '写真1' })).toBeInTheDocument()
  })

  it('対応外の形式は送信せずにエラーを表示し、削除できる', async () => {
    givenLoggedIn()
    let requested = false
    server.use(
      http.post('/api/v1/uploads/presigned-url', () => {
        requested = true
        return HttpResponse.json({})
      }),
    )
    renderApp('/spots/new')
    await screen.findByLabelText('タイトル')

    // accept 属性で弾かれずに選ばれた場合（ドラッグ&ドロップ等）を再現する
    const user = userEvent.setup({ applyAccept: false })
    await user.upload(screen.getByLabelText('写真を追加'), new File(['x'], 'anim.gif', { type: 'image/gif' }))

    expect(await screen.findByText(/対応していない画像形式です/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '再試行' })).not.toBeInTheDocument()
    expect(requested).toBe(false)

    await user.click(screen.getByRole('button', { name: '写真1を削除' }))
    expect(screen.queryByRole('list', { name: '選択した写真' })).not.toBeInTheDocument()
  })

  it('10枚を超える分は追加しない', async () => {
    givenLoggedIn()
    server.use(...uploadHandlers())
    renderApp('/spots/new')
    await screen.findByLabelText('タイトル')

    await userEvent.upload(
      screen.getByLabelText('写真を追加'),
      Array.from({ length: 12 }, (_, i) => jpeg(`${i}.jpg`)),
    )

    expect(await screen.findByText('写真は10枚までです。2枚は追加しませんでした')).toBeInTheDocument()
    expect(within(photoList()).getAllByRole('listitem')).toHaveLength(10)
    // 上限に達したら追加欄を隠す
    expect(screen.queryByLabelText('写真を追加')).not.toBeInTheDocument()
  })

  it('サーバーが写真を拒否したら（INVALID_PHOTO_KEY）写真欄にエラーを表示する', async () => {
    givenLoggedIn()
    server.use(
      ...uploadHandlers(),
      http.post('/api/v1/spots', () =>
        HttpResponse.json(
          errorResponse('INVALID_PHOTO_KEY', '写真を確認できませんでした。もう一度アップロードしてください'),
          { status: 400 },
        ),
      ),
    )
    renderApp('/spots/new')
    await fillRequiredFields()
    await userEvent.upload(screen.getByLabelText('写真を追加'), jpeg('a.jpg'))
    await waitFor(() => expect(screen.queryByRole('progressbar')).not.toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(
      await screen.findByText('写真を確認できませんでした。もう一度アップロードしてください'),
    ).toBeInTheDocument()
  })
})

describe('スポット編集の写真', () => {
  it('既存の写真を表示し、外した写真は送らない', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(
          spotFixture({
            photos: [
              { s3Key: 'spots/1/b.jpg', url: 'https://img.test/spots/1/b.jpg', sortOrder: 1 },
              { s3Key: 'spots/1/a.jpg', url: 'https://img.test/spots/1/a.jpg', sortOrder: 0 },
            ],
          }),
        ),
      ),
      http.put('/api/v1/spots/100', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(spotFixture())
      }),
    )
    renderApp('/spots/100/edit')

    const list = await screen.findByRole('list', { name: '選択した写真' })
    expect(within(list).getByRole('img', { name: '写真1' })).toHaveAttribute(
      'src',
      'https://img.test/spots/1/a.jpg',
    )

    await userEvent.click(screen.getByRole('button', { name: '写真1を削除' }))
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(sentBody).not.toBeNull())
    expect(sentBody).toMatchObject({ photos: [{ s3Key: 'spots/1/b.jpg', sortOrder: 0 }] })
  })
})

describe('写真の表示', () => {
  it('詳細画面では写真を切り替えて見られる', async () => {
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(
          spotFixture({
            photos: [
              { s3Key: 'spots/1/a.jpg', url: 'https://img.test/a.jpg', sortOrder: 0 },
              { s3Key: 'spots/1/b.jpg', url: 'https://img.test/b.jpg', sortOrder: 1 },
            ],
          }),
        ),
      ),
    )
    renderApp('/spots/100')

    const main = await screen.findByRole('img', { name: '朝の清水寺の写真（1/2枚目）' })
    expect(main).toHaveAttribute('src', 'https://img.test/a.jpg')

    await userEvent.click(screen.getByRole('button', { name: '次の写真' }))
    expect(screen.getByRole('img', { name: '朝の清水寺の写真（2/2枚目）' })).toHaveAttribute(
      'src',
      'https://img.test/b.jpg',
    )

    await userEvent.click(screen.getByRole('button', { name: '1枚目の写真を表示' }))
    expect(screen.getByRole('img', { name: '朝の清水寺の写真（1/2枚目）' })).toBeInTheDocument()
  })

  it('写真が無いスポットは写真欄を表示しない', async () => {
    server.use(http.get('/api/v1/spots/100', () => HttpResponse.json(spotFixture())))
    renderApp('/spots/100')

    await screen.findByRole('heading', { name: '朝の清水寺' })
    expect(screen.queryByRole('region', { name: '写真' })).not.toBeInTheDocument()
  })

  it('一覧のカードにカバー写真を表示する', async () => {
    server.use(
      http.get('/api/v1/spots', () =>
        HttpResponse.json({
          items: [
            spotSummaryFixture(1, '写真あり', { coverPhotoUrl: 'https://img.test/cover.jpg' }),
            spotSummaryFixture(2, '写真なし'),
          ],
          nextCursor: null,
        }),
      ),
    )
    const { container } = renderApp('/')

    await screen.findByRole('link', { name: '写真あり' })
    const images = container.querySelectorAll('img')
    expect(images).toHaveLength(1)
    expect(images[0]).toHaveAttribute('src', 'https://img.test/cover.jpg')
  })
})
