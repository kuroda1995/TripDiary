import { expect, test, type Page } from '@playwright/test'

/**
 * プロフィール編集・パスワード変更の E2E（docs/qa_test_plan.md 4.1）。
 * 前提: バックエンド・DB・S3Mock（:9090）が起動していること。
 */

// 2x2 の赤い PNG
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEUlEQVR4nGP4z8DwnwGMgRQAH+4D/dJQfRoAAAAASUVORK5CYII=',
  'base64',
)

type LoadedImage = { complete: boolean; naturalWidth: number }

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`
}

async function signup(page: Page) {
  const suffix = uniqueSuffix()
  const user = {
    email: `e2e_${suffix}@example.com`,
    username: `e2e_${suffix}`.slice(0, 30),
    displayName: `旅人${suffix.slice(-4)}`,
  }
  await page.goto('/signup')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('ユーザー名').fill(user.username)
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('パスワード', { exact: true }).fill('Passw0rd123')
  await page.getByLabel('パスワード（確認）').fill('Passw0rd123')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('link', { name: user.displayName })).toBeVisible()
  return user
}

test('プロフィール（表示名・自己紹介・アイコン）を編集すると、ヘッダー・マイページ・投稿者欄に反映される', async ({
  page,
}) => {
  const user = await signup(page)
  const newName = `温泉${uniqueSuffix().slice(-4)}`

  await page.goto('/me')
  await page.getByRole('link', { name: 'プロフィールを編集' }).click()
  await page.getByLabel('表示名').fill(newName)
  await page.getByLabel('自己紹介（任意）').fill('週末は温泉めぐり♨')
  await page
    .getByLabel('アイコン画像を選ぶ')
    .setInputFiles({ name: 'me.png', mimeType: 'image/png', buffer: PNG })
  await expect(page.getByRole('img', { name: 'アイコンのプレビュー' })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存する' })).toBeEnabled()
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(page.getByText('プロフィールを保存しました。')).toBeVisible()

  // ヘッダーの表示名が変わり、アイコンは S3（S3Mock）から配信される
  const headerLink = page.getByRole('navigation', { name: 'アカウント' }).getByRole('link', { name: newName })
  await expect(headerLink).toBeVisible()
  await expect
    .poll(() => headerLink.locator('img').evaluate((img) => (img as unknown as LoadedImage).naturalWidth))
    .toBe(2)

  // 読み込み直しても保持され、マイページに自己紹介が出る
  // （goto はページの再読み込みになる。reload と続けて呼ぶと、トークン更新の途中で画面を離れて再利用検知に引っかかるため1回だけ）
  await page.goto('/me')
  await expect(page.getByText('週末は温泉めぐり♨')).toBeVisible()
  await expect(page.getByText(`@${user.username}`)).toBeVisible()
})

test('パスワードを変更すると、新しいパスワードでだけログインできる', async ({ page }) => {
  const user = await signup(page)

  await page.goto('/settings/password')
  await page.getByLabel('現在のパスワード').fill('Passw0rd123')
  await page.getByLabel('新しいパスワード', { exact: true }).fill('NewPass456')
  await page.getByLabel('新しいパスワード（確認）').fill('NewPass456')
  await page.getByRole('button', { name: '変更する' }).click()
  await expect(page.getByText('パスワードを変更しました。')).toBeVisible()

  // この端末はログインしたまま（再読み込みしても新しいリフレッシュトークンで復元できる）
  await page.reload()
  await expect(page.getByRole('link', { name: user.displayName })).toBeVisible()

  await page.getByRole('button', { name: 'ログアウト' }).click()
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill('Passw0rd123')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByText('メールアドレスまたはパスワードが正しくありません')).toBeVisible()

  await page.getByLabel('パスワード').fill('NewPass456')
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByRole('link', { name: user.displayName })).toBeVisible()
})
