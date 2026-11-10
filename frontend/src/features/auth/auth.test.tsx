import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { authResponse, errorResponse, meFixture } from '@/test/msw/handlers'
import { server } from '@/test/msw/server'
import { renderApp } from '@/test/render'

/** ログイン済み状態で起動する（リフレッシュトークン Cookie がある状態）。 */
function givenLoggedIn() {
  server.use(
    http.post('/api/v1/auth/refresh', () => HttpResponse.json(authResponse())),
    http.get('/api/v1/users/me', () => HttpResponse.json(meFixture)),
  )
}

describe('ログイン状態の復元・ヘッダー', () => {
  it('リフレッシュトークンがあればログイン状態を復元し、ヘッダーに表示名を出す', async () => {
    givenLoggedIn()

    renderApp('/')

    expect(await screen.findByRole('link', { name: '旅するタロウ' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'ログアウト' })).toBeInTheDocument()
  })

  it('リフレッシュトークンが無ければ未ログインとして「ログイン」「新規登録」を出す', async () => {
    renderApp('/')

    expect(await screen.findByRole('link', { name: 'ログイン' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '新規登録' })).toBeInTheDocument()
  })

  it('ログアウトするとログアウト API を呼び、未ログイン表示に戻る', async () => {
    givenLoggedIn()
    let logoutCalled = false
    server.use(
      http.post('/api/v1/auth/logout', () => {
        logoutCalled = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'ログアウト' }))

    expect(await screen.findByRole('link', { name: 'ログイン' })).toBeInTheDocument()
    expect(logoutCalled).toBe(true)
  })

  it('ログアウト API が失敗しても、この端末ではログアウト状態にする', async () => {
    givenLoggedIn()
    server.use(http.post('/api/v1/auth/logout', () => HttpResponse.error()))
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'ログアウト' }))

    expect(await screen.findByRole('link', { name: 'ログイン' })).toBeInTheDocument()
  })
})

describe('認証ガード', () => {
  it('未ログインでマイページを開くと、元の画面を redirect に付けてログイン画面へ移動する', async () => {
    renderApp('/me')

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
  })

  it('ログイン済みならマイページにアカウント情報を表示する', async () => {
    givenLoggedIn()

    renderApp('/me')

    expect(await screen.findByRole('heading', { name: 'マイページ' })).toBeInTheDocument()
    expect(await screen.findByText('taro@example.com')).toBeInTheDocument()
    expect(screen.getByText('@taro_trip')).toBeInTheDocument()
  })

  it('ログイン済みでログイン画面を開くとトップへ移動する', async () => {
    givenLoggedIn()

    renderApp('/login')

    expect(await screen.findByRole('heading', { name: '旅の思い出を、みんなと。' })).toBeInTheDocument()
  })
})

describe('ログイン画面', () => {
  it('未入力で送信すると項目ごとにエラーを表示し、API は呼ばない', async () => {
    let called = false
    server.use(
      http.post('/api/v1/auth/login', () => {
        called = true
        return HttpResponse.json(authResponse())
      }),
    )
    renderApp('/login')

    await userEvent.click(await screen.findByRole('button', { name: 'ログイン' }))

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument()
    expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument()
    expect(screen.getByLabelText('メールアドレス')).toHaveAttribute('aria-invalid', 'true')
    expect(called).toBe(false)
  })

  it('ログインに成功すると redirect の画面へ移動する', async () => {
    let sentBody: unknown = null
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(authResponse())
      }),
      http.get('/api/v1/users/me', () => HttpResponse.json(meFixture)),
    )
    renderApp('/login?redirect=%2Fme')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'Passw0rd123')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('heading', { name: 'マイページ' })).toBeInTheDocument()
    expect(sentBody).toEqual({ email: 'taro@example.com', password: 'Passw0rd123' })
  })

  it('外部サイトの redirect は無視してトップへ移動する', async () => {
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.json(authResponse())))
    renderApp('/login?redirect=https%3A%2F%2Fevil.example.com')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'Passw0rd123')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('heading', { name: '旅の思い出を、みんなと。' })).toBeInTheDocument()
  })

  it('メールアドレスかパスワードが違う場合はサーバーのメッセージを表示する', async () => {
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          errorResponse('INVALID_CREDENTIALS', 'メールアドレスまたはパスワードが正しくありません'),
          { status: 401 },
        ),
      ),
    )
    renderApp('/login')

    await userEvent.type(await screen.findByLabelText('メールアドレス'), 'taro@example.com')
    await userEvent.type(screen.getByLabelText('パスワード'), 'Wrong0000')
    await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'メールアドレスまたはパスワードが正しくありません',
    )
  })
})

describe('新規登録画面', () => {
  async function fillSignupForm(overrides: Partial<Record<string, string>> = {}) {
    const values = {
      メールアドレス: 'taro@example.com',
      ユーザー名: 'taro_trip',
      表示名: '旅するタロウ',
      パスワード: 'Passw0rd123',
      'パスワード（確認）': 'Passw0rd123',
      ...overrides,
    }
    for (const [label, value] of Object.entries(values)) {
      if (value) await userEvent.type(screen.getByLabelText(label), value)
    }
  }

  it('登録に成功するとログイン状態になり、トップへ移動する', async () => {
    let sentBody: unknown = null
    server.use(
      http.post('/api/v1/auth/signup', async ({ request }) => {
        sentBody = await request.json()
        return HttpResponse.json(authResponse(), { status: 201 })
      }),
    )
    renderApp('/signup')
    await screen.findByRole('heading', { name: '新規登録' })

    await fillSignupForm()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByRole('link', { name: '旅するタロウ' })).toBeInTheDocument()
    // 確認用パスワードはサーバーに送らない
    expect(sentBody).toEqual({
      email: 'taro@example.com',
      username: 'taro_trip',
      displayName: '旅するタロウ',
      password: 'Passw0rd123',
    })
  })

  it('パスワードのルール違反・確認用パスワードの不一致は送信前にエラーを表示する', async () => {
    renderApp('/signup')
    await screen.findByRole('heading', { name: '新規登録' })

    await fillSignupForm({ パスワード: 'onlyletters', 'パスワード（確認）': 'different1' })
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText(/英字と数字をそれぞれ1文字以上/)).toBeInTheDocument()
    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
  })

  it('メールアドレスが登録済みの場合は、メールアドレス欄にエラーを表示する', async () => {
    server.use(
      http.post('/api/v1/auth/signup', () =>
        HttpResponse.json(errorResponse('EMAIL_ALREADY_EXISTS', 'このメールアドレスは登録済みです'), {
          status: 409,
        }),
      ),
    )
    renderApp('/signup')
    await screen.findByRole('heading', { name: '新規登録' })

    await fillSignupForm()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText('このメールアドレスは登録済みです')).toBeInTheDocument()
    expect(screen.getByLabelText('メールアドレス')).toHaveAttribute('aria-invalid', 'true')
  })

  it('サーバーの入力エラー（VALIDATION_ERROR）は該当する項目に表示する', async () => {
    server.use(
      http.post('/api/v1/auth/signup', () =>
        HttpResponse.json(
          errorResponse('VALIDATION_ERROR', '入力内容に誤りがあります', [
            { field: 'username', message: 'サーバー側のユーザー名エラー' },
          ]),
          { status: 400 },
        ),
      ),
    )
    renderApp('/signup')
    await screen.findByRole('heading', { name: '新規登録' })

    await fillSignupForm()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    expect(await screen.findByText('サーバー側のユーザー名エラー')).toBeInTheDocument()
  })

  it('通信エラーなど項目に対応しないエラーはフォーム上部に表示する', async () => {
    server.use(http.post('/api/v1/auth/signup', () => HttpResponse.error()))
    renderApp('/signup')
    await screen.findByRole('heading', { name: '新規登録' })

    await fillSignupForm()
    await userEvent.click(screen.getByRole('button', { name: '登録する' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('通信エラー'))
  })
})
