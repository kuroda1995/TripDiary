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

test.describe('参考になった・行きたい・称号', () => {
  test('投稿すると称号が付き、別のユーザーが参考になった・行きたいを付けてマイページで確認できる', async ({
    page,
    browser,
  }) => {
    const author = await signup(page)
    const title = `E2E 長谷寺 ${uniqueSuffix()}`

    await page.getByRole('link', { name: '投稿する' }).click()
    await page.getByLabel('タイトル').fill(title)
    await page.getByLabel('場所').fill('長谷寺（神奈川県鎌倉市）')
    await page.getByText('関東').click()
    await page.getByRole('checkbox', { name: '神奈川' }).check()
    await page.getByRole('button', { name: '投稿する' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    const spotUrl = page.url()

    // 1件投稿したので、プロフィールに「参拝ビギナー」が付く
    await page.goto(`/users/${author.username}`)
    await expect(page.getByText('参拝ビギナー').first()).toBeVisible()

    // 別のユーザーが参考になった・行きたいを付ける
    const other = await browser.newPage()
    await signup(other)
    await other.goto(spotUrl)
    const helpful = other.getByRole('button', { name: /参考になった/ })
    await helpful.click()
    await expect(helpful).toHaveAttribute('aria-pressed', 'true')
    await expect(helpful).toContainText('1')
    const wantToGo = other.getByRole('button', { name: '行きたい' })
    await wantToGo.click()
    await expect(wantToGo).toHaveAttribute('aria-pressed', 'true')

    // 再読み込みしても残っている
    await other.reload()
    await expect(other.getByRole('button', { name: /参考になった/ })).toHaveAttribute('aria-pressed', 'true')

    // マイページの「行きたい」タブに表示される
    await other.goto('/me')
    await expect(other.getByRole('link', { name: title })).toBeVisible()
    await other.close()
  })
})
