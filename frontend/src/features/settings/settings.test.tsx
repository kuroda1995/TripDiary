import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Me } from '@/features/auth/types'
import { getAccessToken } from '@/lib/authSession'
import {
  authResponse,
  errorResponse,
  meFixture,
  spotFixture,
  storagePutHandler,
  uploadHandlers,
} from '@/test/msw/handlers'
import { imageFile, pngOfSize } from '@/test/images'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

function givenLoggedIn(user: Me = meFixture) {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse(user))),
    http.get('/api/v1/users/me', () => HttpResponse.json(user)),
  )
}

function header() {
  return screen.getByRole('navigation', { name: 'アカウント' })
}

describe('プロフィール編集', () => {
  it('未ログインで開くとログイン画面へ移動する', async () => {
    renderApp('/settings/profile')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('表示名・自己紹介を保存すると PATCH し、ヘッダーの表示名も変わる（アイコンは変えていないので送らない）', async () => {
    givenLoggedIn({ ...meFixture, bio: '旧プロフィール' })
    let sentBody: unknown = null
    server.use(
      http.patch('/api/v1/users/me', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json({ ...meFixture, displayName: '温泉タロウ', bio: '週末は温泉めぐり' })
      }),
    )
    renderApp('/settings/profile')

    const displayName = await screen.findByLabelText('表示名')
    expect(displayName).toHaveValue('旅するタロウ')
    expect(screen.getByLabelText('自己紹介（任意）')).toHaveValue('旧プロフィール')

    await userEvent.clear(displayName)
    await userEvent.type(displayName, '温泉タロウ')
    await userEvent.clear(screen.getByLabelText('自己紹介（任意）'))
    await userEvent.type(screen.getByLabelText('自己紹介（任意）'), '週末は温泉めぐり')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('プロフィールを保存しました。')).toBeInTheDocument()
    expect(sentBody).toEqual({ displayName: '温泉タロウ', bio: '週末は温泉めぐり' })
    expect(within(header()).getByRole('link', { name: '温泉タロウ' })).toBeInTheDocument()
  })

  it('自己紹介を空にすると null を送る', async () => {
    givenLoggedIn({ ...meFixture, bio: '消す予定' })
    let sentBody: unknown = null
    server.use(
      http.patch('/api/v1/users/me', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(meFixture)
      }),
    )
    renderApp('/settings/profile')

    await userEvent.clear(await screen.findByLabelText('自己紹介（任意）'))
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(sentBody).toMatchObject({ bio: null }))
  })

  it('表示名が空・自己紹介が501文字なら送信前にエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/settings/profile')

    await userEvent.clear(await screen.findByLabelText('表示名'))
    await userEvent.click(screen.getByLabelText('自己紹介（任意）'))
    await userEvent.paste('あ'.repeat(501))
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('表示名を入力してください')).toBeInTheDocument()
    expect(screen.getByText('自己紹介は500文字以内で入力してください')).toBeInTheDocument()
    expect(screen.getByText('501 / 500')).toBeInTheDocument()
  })

  it('アイコンを選ぶと AVATAR 用にアップロードし、保存でキーを送る', async () => {
    givenLoggedIn()
    let purpose: unknown = null
    let sentBody: unknown = null
    server.use(...uploadHandlers())
    server.use(
      http.post('/api/v1/uploads/presigned-url', async ({ request }) => {
        purpose = ((await request.json()) as { purpose: string }).purpose
        return HttpResponse.json({
          uploadUrl: 'http://storage.test/tripdiary-photos/avatars/1/a.png?X-Amz-Signature=x',
          s3Key: 'avatars/1/a.png',
          expiresAt: '2026-11-11T03:20:42Z',
        })
      }),
      http.patch('/api/v1/users/me', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json({ ...meFixture, avatarUrl: 'https://img.test/avatars/1/a.png' })
      }),
    )
    renderApp('/settings/profile')

    await userEvent.upload(
      await screen.findByLabelText('アイコン画像を選ぶ'),
      imageFile('me.png', 'image/png'),
    )

    expect(await screen.findByRole('img', { name: 'アイコンのプレビュー' })).toBeInTheDocument()
    expect(purpose).toBe('AVATAR')
    await userEvent.click(await screen.findByRole('button', { name: '保存する' }))

    await waitFor(() => expect(sentBody).toMatchObject({ avatarS3Key: 'avatars/1/a.png' }))
    await waitFor(() =>
      expect(screen.getByRole('img', { name: 'アイコンのプレビュー' })).toHaveAttribute(
        'src',
        'https://img.test/avatars/1/a.png',
      ),
    )
  })

  it('5MB を超える画像はアップロードせずにエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/settings/profile')

    await userEvent.upload(
      await screen.findByLabelText('アイコン画像を選ぶ'),
      imageFile('big.png', 'image/png', pngOfSize(5 * 1024 * 1024 + 1)),
    )

    expect(await screen.findByText('画像のサイズは5MBまでです')).toBeInTheDocument()
  })

  it('アップロードに失敗したらエラーを表示し、保存ボタンは押せるまま', async () => {
    givenLoggedIn()
    server.use(...uploadHandlers())
    server.use(storagePutHandler(() => 500))
    renderApp('/settings/profile')

    await userEvent.upload(
      await screen.findByLabelText('アイコン画像を選ぶ'),
      imageFile('me.png', 'image/png'),
    )

    expect(await screen.findByText('アップロードに失敗しました')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled()
  })

  it('設定済みのアイコンを削除すると avatarS3Key: null を送る', async () => {
    givenLoggedIn({ ...meFixture, avatarUrl: 'https://img.test/avatars/1/old.png' })
    let sentBody: unknown = null
    server.use(
      http.patch('/api/v1/users/me', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(meFixture)
      }),
    )
    renderApp('/settings/profile')

    expect(await screen.findByRole('img', { name: 'アイコンのプレビュー' })).toHaveAttribute(
      'src',
      'https://img.test/avatars/1/old.png',
    )
    await userEvent.click(screen.getByRole('button', { name: 'アイコンを削除' }))
    expect(screen.queryByRole('img', { name: 'アイコンのプレビュー' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    await waitFor(() => expect(sentBody).toMatchObject({ avatarS3Key: null }))
  })

  it('サーバーがアイコンを拒否したらフォームにエラーを表示する', async () => {
    givenLoggedIn()
    server.use(
      http.patch('/api/v1/users/me', () =>
        HttpResponse.json(
          errorResponse(
            'INVALID_AVATAR_KEY',
            'アイコン画像を確認できませんでした。もう一度アップロードしてください',
          ),
          { status: 400 },
        ),
      ),
    )
    renderApp('/settings/profile')

    await userEvent.click(await screen.findByRole('button', { name: '保存する' }))

    expect(
      await screen.findByText('アイコン画像を確認できませんでした。もう一度アップロードしてください'),
    ).toBeInTheDocument()
  })
})

describe('パスワード変更', () => {
  async function fill(current: string, next: string, confirmation = next) {
    await userEvent.type(await screen.findByLabelText('現在のパスワード'), current)
    await userEvent.type(screen.getByLabelText('新しいパスワード'), next)
    await userEvent.type(screen.getByLabelText('新しいパスワード（確認）'), confirmation)
    await userEvent.click(screen.getByRole('button', { name: '変更する' }))
  }

  it('変更すると新しいアクセストークンに切り替わり、完了メッセージを表示して入力欄を空にする', async () => {
    givenLoggedIn()
    let sentBody: unknown = null
    server.use(
      http.put('/api/v1/users/me/password', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(authResponse(meFixture, 'access-token-2'))
      }),
    )
    renderApp('/settings/password')

    await fill('Passw0rd123', 'NewPass456')

    expect(await screen.findByText('パスワードを変更しました。')).toBeInTheDocument()
    expect(sentBody).toEqual({ currentPassword: 'Passw0rd123', newPassword: 'NewPass456' })
    expect(getAccessToken()).toBe('access-token-2')
    expect(screen.getByLabelText('現在のパスワード')).toHaveValue('')
  })

  it('確認用が一致しない・現在と同じ・弱いパスワードは送信前にエラーを表示する', async () => {
    givenLoggedIn()
    renderApp('/settings/password')

    await fill('Passw0rd123', 'NewPass456', 'NewPass457')
    expect(await screen.findByText('パスワードが一致しません')).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText('新しいパスワード'))
    await userEvent.type(screen.getByLabelText('新しいパスワード'), 'Passw0rd123')
    await userEvent.clear(screen.getByLabelText('新しいパスワード（確認）'))
    await userEvent.type(screen.getByLabelText('新しいパスワード（確認）'), 'Passw0rd123')
    await userEvent.click(screen.getByRole('button', { name: '変更する' }))
    expect(await screen.findByText('新しいパスワードが現在のパスワードと同じです')).toBeInTheDocument()

    await userEvent.clear(screen.getByLabelText('新しいパスワード'))
    await userEvent.type(screen.getByLabelText('新しいパスワード'), 'short')
    await userEvent.click(screen.getByRole('button', { name: '変更する' }))
    expect(await screen.findByText(/パスワードは8〜72文字/)).toBeInTheDocument()
  })

  it('現在のパスワードが違うと、その欄にエラーを表示する', async () => {
    givenLoggedIn()
    server.use(
      http.put('/api/v1/users/me/password', () =>
        HttpResponse.json(errorResponse('INVALID_CURRENT_PASSWORD', '現在のパスワードが正しくありません'), {
          status: 400,
        }),
      ),
    )
    renderApp('/settings/password')

    await fill('Wrong1234', 'NewPass456')

    expect(await screen.findByText('現在のパスワードが正しくありません')).toBeInTheDocument()
    expect(screen.getByLabelText('現在のパスワード')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('アイコンの表示', () => {
  it('マイページにアイコン・自己紹介と設定へのリンクを表示する', async () => {
    givenLoggedIn({ ...meFixture, bio: '週末は温泉めぐり', avatarUrl: 'https://img.test/a.png' })
    const { container } = renderApp('/me')

    expect(await screen.findByText('週末は温泉めぐり')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute(
      'href',
      '/settings/profile',
    )
    expect(screen.getByRole('link', { name: 'パスワードを変更' })).toHaveAttribute(
      'href',
      '/settings/password',
    )
    expect(container.querySelector('img[src="https://img.test/a.png"]')).not.toBeNull()
  })

  it('スポット詳細で投稿者のアイコンを表示し、未設定なら頭文字を表示する', async () => {
    server.use(
      http.get('/api/v1/spots/100', () =>
        HttpResponse.json(
          spotFixture({
            author: { id: 2, username: 'hanako', displayName: 'はなこ', avatarUrl: 'https://img.test/h.png' },
          }),
        ),
      ),
      http.get('/api/v1/spots/101', () =>
        HttpResponse.json(
          spotFixture({ id: 101, author: { id: 3, username: 'jiro', displayName: '次郎', avatarUrl: null } }),
        ),
      ),
    )
    const first = renderApp('/spots/100')
    const link = await screen.findByRole('link', { name: 'はなこ' })
    expect(link.querySelector('img')).toHaveAttribute('src', 'https://img.test/h.png')
    first.unmount()

    renderApp('/spots/101')
    const jiro = await screen.findByRole('link', { name: '次郎' })
    expect(jiro.querySelector('img')).toBeNull()
    expect(jiro).toHaveTextContent('次')
  })
})
