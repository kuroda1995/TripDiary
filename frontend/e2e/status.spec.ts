import { expect, test, type Page } from '@playwright/test'

/** ステータス管理（マイリスト）の E2E（docs/qa_test_plan.md 4.7）。 */

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

test('「行きたい」に入れたスポットをマイページで「行った！」にすると訪問済みに移り、他人には見えない', async ({
  page,
  browser,
}) => {
  // 投稿者
  await signup(page)
  const title = `E2E マイリスト ${uniqueSuffix()}`
  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('白川郷')
  await page.getByText('中部').click()
  await page.getByRole('checkbox', { name: '岐阜' }).check()
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  const spotUrl = page.url()

  // 別のユーザーが「行きたい」に入れる
  const other = await browser.newPage()
  await signup(other)
  await other.goto(spotUrl)
  const select = other.getByRole('combobox', { name: 'マイリスト' })
  await expect(select).toHaveValue('')
  await select.selectOption('WANT_TO_GO')
  await expect(select).toBeEnabled()

  // 再読み込みしても保持されている
  await other.reload()
  await expect(other.getByRole('combobox', { name: 'マイリスト' })).toHaveValue('WANT_TO_GO')

  // マイページの「行きたい」タブに出て、「行った！」で訪問済みへ
  await other.goto('/me')
  await expect(other.getByRole('tab', { name: '行きたい（1）' })).toHaveAttribute('aria-selected', 'true')
  const wantList = other.getByRole('list', { name: '行きたいのスポット' })
  await expect(wantList.getByRole('link', { name: title })).toBeVisible()
  await wantList.getByRole('button', { name: `「${title}」に行った！（訪問済みにする）` }).click()
  await expect(other.getByText(/まだ「行きたい」スポットはありません/)).toBeVisible()
  await expect(other.getByRole('tab', { name: '訪問済み（1）' })).toBeVisible()

  await other.getByRole('tab', { name: '訪問済み（1）' }).click()
  await expect(other).toHaveURL(/\/me\?tab=visited$/)
  await expect(
    other.getByRole('list', { name: '訪問済みのスポット' }).getByRole('link', { name: title }),
  ).toBeVisible()

  // 投稿者のマイリストには出ず、詳細のステータスも未登録のまま（本人だけのリスト）
  await page.goto('/me?tab=visited')
  await expect(page.getByText('まだ「訪問済み」のスポットはありません。')).toBeVisible()
  await page.goto(spotUrl)
  await expect(page.getByRole('combobox', { name: 'マイリスト' })).toHaveValue('')

  // 解除するとリストから消える
  await other.goto(spotUrl)
  await other.getByRole('combobox', { name: 'マイリスト' }).selectOption('')
  await expect(other.getByRole('combobox', { name: 'マイリスト' })).toBeEnabled()
  await other.goto('/me?tab=visited')
  await expect(other.getByText('まだ「訪問済み」のスポットはありません。')).toBeVisible()
})
