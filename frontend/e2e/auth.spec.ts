import { expect, test, type Page } from '@playwright/test'

/** 実行ごとに一意なユーザー（DB を共有しても衝突しない）。 */
function uniqueUser() {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`
  return {
    email: `e2e_${suffix}@example.com`,
    username: `e2e_${suffix}`.slice(0, 30),
    displayName: 'E2E テスター',
    password: 'Passw0rd123',
  }
}

async function signup(page: Page, user: ReturnType<typeof uniqueUser>) {
  await page.goto('/signup')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('ユーザー名').fill(user.username)
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('パスワード', { exact: true }).fill(user.password)
  await page.getByLabel('パスワード（確認）').fill(user.password)
  await page.getByRole('button', { name: '登録する' }).click()
}

test.describe('ユーザー認証', () => {
  test('新規登録 → リロードしてもログイン状態を保持 → ログアウト → ログインで元の画面に戻る', async ({
    page,
  }) => {
    const user = uniqueUser()

    // 新規登録するとログイン状態になる
    await signup(page, user)
    const accountLink = page.getByRole('link', { name: user.displayName })
    await expect(accountLink).toBeVisible()

    // マイページにアカウント情報が表示される
    await accountLink.click()
    await expect(page.getByRole('heading', { name: 'マイページ' })).toBeVisible()
    await expect(page.getByText(user.email)).toBeVisible()

    // リロードしても（アクセストークンはメモリから消えるが）Cookie のリフレッシュトークンで復元される
    await page.reload()
    await expect(page.getByText(user.email)).toBeVisible()

    // ログアウトすると未ログイン表示に戻り、マイページはログイン画面へ移動する
    await page.getByRole('button', { name: 'ログアウト' }).click()
    await expect(page.getByRole('link', { name: 'ログイン' })).toBeVisible()
    await page.goto('/me')
    await expect(page).toHaveURL(/\/login\?redirect=%2Fme$/)

    // ログインするとマイページに戻る
    await page.getByLabel('メールアドレス').fill(user.email)
    await page.getByLabel('パスワード').fill(user.password)
    await page.getByRole('button', { name: 'ログイン' }).click()
    await expect(page).toHaveURL('/me')
    await expect(page.getByText(user.email)).toBeVisible()
  })

  test('パスワードが違うとエラーメッセージを表示する', async ({ page }) => {
    const user = uniqueUser()
    await signup(page, user)
    await page.getByRole('button', { name: 'ログアウト' }).click()
    // ログアウト（リフレッシュトークンの失効）の完了を待ってから移動する
    await expect(page.getByRole('link', { name: 'ログイン' })).toBeVisible()

    await page.goto('/login')
    await page.getByLabel('メールアドレス').fill(user.email)
    await page.getByLabel('パスワード').fill('Wrong0000')
    await page.getByRole('button', { name: 'ログイン' }).click()

    await expect(page.getByRole('alert')).toHaveText('メールアドレスまたはパスワードが正しくありません')
  })

  test('登録済みのメールアドレスでは登録できない', async ({ page, browser }) => {
    const user = uniqueUser()
    await signup(page, user)
    await expect(page.getByRole('link', { name: user.displayName })).toBeVisible()

    // 別のブラウザ（未ログイン）で同じメールアドレスを使う
    const other = await browser.newPage()
    await signup(other, { ...user, username: `${user.username.slice(0, 27)}_2` })

    await expect(other.getByText('このメールアドレスは登録済みです')).toBeVisible()
    await other.close()
  })
})
