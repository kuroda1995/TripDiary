import { expect, test, type Page } from '@playwright/test'

/** 行ってみたい！の E2E（docs/qa_test_plan.md 4.3）。 */

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`
}

async function signup(page: Page) {
  const suffix = uniqueSuffix()
  const displayName = `旅人${suffix.slice(-4)}`
  await page.goto('/signup')
  await page.getByLabel('メールアドレス').fill(`e2e_${suffix}@example.com`)
  await page.getByLabel('ユーザー名').fill(`e2e_${suffix}`.slice(0, 30))
  await page.getByLabel('表示名').fill(displayName)
  await page.getByLabel('パスワード', { exact: true }).fill('Passw0rd123')
  await page.getByLabel('パスワード（確認）').fill('Passw0rd123')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('link', { name: displayName })).toBeVisible()
}

test('他のユーザーの投稿に行ってみたい！を付けると件数が増え、マイページの一覧に出て、外すと元に戻る', async ({
  page,
  browser,
}) => {
  // 投稿者
  await signup(page)
  const title = `E2E 行ってみたい ${uniqueSuffix()}`
  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('伏見稲荷大社')
  await page.getByText('関西').click()
  await page.getByRole('checkbox', { name: '京都' }).check()
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  const spotUrl = page.url()

  // 別のユーザーが詳細画面で付ける
  const other = await browser.newPage()
  await signup(other)
  await other.goto(spotUrl)
  const like = other.getByRole('button', { name: /行ってみたい！/ })
  await expect(like).toHaveText(/0$/)
  await like.click()
  await expect(like).toHaveAttribute('aria-pressed', 'true')
  await expect(like).toHaveText(/1$/)

  // 再読み込みしても保持されている
  await other.reload()
  await expect(other.getByRole('button', { name: /行ってみたい！/ })).toHaveAttribute('aria-pressed', 'true')

  // マイページの「行ってみたい！した投稿」に出る
  await other.goto('/me')
  const myLikes = other.getByRole('list', { name: '行ってみたい！した投稿' })
  await expect(myLikes.getByRole('link', { name: title })).toBeVisible()

  // 投稿者からも件数が見える（自分は付けていない）
  await page.reload()
  await expect(page.getByRole('button', { name: /行ってみたい！/ })).toHaveText(/1$/)
  await expect(page.getByRole('button', { name: /行ってみたい！/ })).toHaveAttribute('aria-pressed', 'false')

  // マイページの一覧から外す
  const card = myLikes.locator(':scope > li').filter({ hasText: title })
  await card.getByRole('button', { name: /行ってみたい！/ }).click()
  await expect(card.getByRole('button', { name: /行ってみたい！/ })).toHaveText(/0$/)
  await other.close()
})
