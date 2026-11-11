import { expect, test, type Page } from '@playwright/test'

/** コメントの E2E（docs/qa_test_plan.md 4.4）。 */

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
  return displayName
}

test('コメントを投稿・編集でき、HTML は文字として表示され、スポットの投稿者は他人のコメントを削除できる', async ({
  page,
  browser,
}) => {
  // 投稿者がスポットを作る
  await signup(page)
  const title = `E2E コメント ${uniqueSuffix()}`
  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('嵐山')
  await page.getByText('関西').click()
  await page.getByRole('checkbox', { name: '京都' }).check()
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  const spotUrl = page.url()

  // 別のユーザーがコメントする（スクリプトを含む本文）
  const other = await browser.newPage()
  const otherName = await signup(other)
  let alerted = false
  other.on('dialog', async (dialog) => {
    alerted = true
    await dialog.dismiss()
  })
  await other.goto(spotUrl)
  const xss = '<img src=x onerror=alert(1)>何時ごろ行きましたか？'
  await other.getByLabel('コメントを書く').fill(xss)
  await other.getByRole('button', { name: 'コメントする' }).click()

  const comment = other.getByRole('article', { name: `${otherName}のコメント` })
  await expect(comment.getByText(xss)).toBeVisible()
  await expect(comment.locator('img[src="x"]')).toHaveCount(0)
  await expect(other.getByRole('heading', { name: 'コメント（1件）' })).toBeVisible()
  expect(alerted).toBe(false)

  // 編集すると「（編集済み）」になる
  await comment.getByRole('button', { name: '編集' }).click()
  await comment.getByLabel('コメントを編集').fill('何時ごろ行きましたか？朝ですか？')
  await comment.getByRole('button', { name: '保存する' }).click()
  await expect(comment.getByText('何時ごろ行きましたか？朝ですか？')).toBeVisible()
  await expect(comment.getByText('（編集済み）')).toBeVisible()
  await other.close()

  // スポットの投稿者は他人のコメントを削除できる（編集はできない）
  await page.reload()
  const onMySpot = page.getByRole('article', { name: `${otherName}のコメント` })
  await expect(onMySpot.getByRole('button', { name: '編集' })).toHaveCount(0)
  await onMySpot.getByRole('button', { name: '削除' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click()
  await expect(page.getByText('まだコメントはありません。')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'コメント（0件）' })).toBeVisible()
})
