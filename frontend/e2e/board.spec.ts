import { expect, test, type Page } from '@playwright/test'

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`
}

async function signup(page: Page) {
  const suffix = uniqueSuffix()
  const user = { username: `e2e_${suffix}`.slice(0, 30), displayName: `参拝者${suffix.slice(-4)}` }
  await page.goto('/signup')
  await page.getByLabel('メールアドレス').fill(`e2e_${suffix}@example.com`)
  await page.getByLabel('ユーザー名').fill(user.username)
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('パスワード', { exact: true }).fill('Passw0rd123')
  await page.getByLabel('パスワード（確認）').fill('Passw0rd123')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('link', { name: user.displayName })).toBeVisible()
  return user
}

test.describe('お寺コミュニティ広場', () => {
  test('トピック作成 → 別のユーザーが返信 → 一覧で返信数が増える → 作成者が削除', async ({
    page,
    browser,
  }) => {
    await signup(page)
    const title = `E2E 御朱印情報交換 ${uniqueSuffix()}`

    // ヘッダーから広場を開いてトピックを作る
    await page.getByRole('link', { name: 'コミュニティ広場' }).click()
    await expect(page.getByRole('heading', { name: 'お寺コミュニティ広場' })).toBeVisible()
    await page.getByLabel('タイトル').fill(title)
    await page.getByLabel('本文').fill('最近いただいた御朱印を教えてください。')
    await page.getByRole('button', { name: 'トピックを作成する' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    const topicUrl = page.url()

    // 別のユーザーが返信する
    const other = await browser.newPage()
    await signup(other)
    await other.goto(topicUrl)
    await other.getByLabel('返信を書く').fill('瑞龍寺で書き置きの御朱印をいただきました')
    await other.getByRole('button', { name: '返信する' }).click()
    await expect(
      other.getByRole('list', { name: '返信' }).getByText('瑞龍寺で書き置きの御朱印をいただきました'),
    ).toBeVisible()
    await expect(other.getByRole('heading', { name: '返信（1件）' })).toBeVisible()
    await other.close()

    // 一覧で返信数が見える（返信があったトピックが先頭に上がる）
    await page.goto('/community')
    const topics = page.getByRole('list', { name: 'トピック' })
    const first = topics.getByRole('article').first()
    await expect(first.getByRole('link', { name: title })).toBeVisible()
    await expect(first.getByText('返信 1件')).toBeVisible()

    // 作成者が削除すると広場へ戻り、一覧から消える
    await topics.getByRole('link', { name: title }).click()
    await page.getByRole('button', { name: '削除' }).first().click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click()
    await expect(page.getByRole('heading', { name: 'お寺コミュニティ広場' })).toBeVisible()
    await expect(page.getByRole('link', { name: title })).toHaveCount(0)
  })
})
