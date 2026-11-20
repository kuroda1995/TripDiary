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

test.describe('コミュニティ', () => {
  test('作成 → 検索で見つかる → 別のユーザーが参加してトピックを作成 → オーナーが返信 → 退出 → オーナーが削除', async ({
    page,
    browser,
  }) => {
    await signup(page)
    const name = `E2E 庭園の会 ${uniqueSuffix()}`

    // ヘッダーから一覧を開いて作成する
    await page.getByRole('link', { name: 'コミュニティ', exact: true }).click()
    await page.getByRole('link', { name: 'コミュニティを作る' }).click()
    await page.getByLabel('コミュニティ名').fill(name)
    await page.getByLabel('説明文（テーマ・目的）').fill('枯山水や日本庭園の見どころを話しましょう。')
    await page.getByRole('checkbox', { name: '建築・庭園' }).check()
    await page.getByRole('button', { name: 'コミュニティを作成する' }).click()
    await expect(page.getByRole('heading', { name })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'メンバー（1人）' })).toBeVisible()
    const communityUrl = page.url()

    // 別のユーザーがキーワードとカテゴリで探して参加する
    const other = await browser.newPage()
    await signup(other)
    await other.goto('/communities')
    await other.getByLabel('キーワード（名前・説明文）').fill(name)
    await other.getByRole('button', { name: '検索' }).click()
    await other.getByRole('button', { name: '建築・庭園' }).click()
    await other.getByRole('list', { name: 'コミュニティ' }).getByRole('link', { name }).click()
    await expect(other.getByText('コミュニティに参加すると、トピックの作成や返信ができます。')).toBeVisible()
    await other.getByRole('button', { name: 'コミュニティに参加する' }).click()
    await expect(other.getByRole('heading', { name: 'メンバー（2人）' })).toBeVisible()

    // 参加したのでトピックを作れる
    const title = `龍安寺の石庭 ${uniqueSuffix()}`
    await other.getByLabel('タイトル').fill(title)
    await other.getByLabel('本文').fill('朝いちばんがおすすめです。')
    await other.getByRole('button', { name: 'トピックを作成する' }).click()
    await expect(other.getByRole('heading', { name: title })).toBeVisible()
    const topicUrl = other.url()

    // オーナーが返信する（広場ではなくコミュニティのトピック）
    await page.goto(topicUrl)
    await expect(page.getByRole('link', { name: `← ${name}` })).toBeVisible()
    await page.getByLabel('返信を書く').fill('ようこそ！')
    await page.getByRole('button', { name: '返信する' }).click()
    await expect(page.getByRole('list', { name: '返信' }).getByText('ようこそ！')).toBeVisible()

    // 退出すると返信できなくなる
    await other.goto(communityUrl)
    await other.getByRole('button', { name: '退出する' }).click()
    await expect(other.getByRole('button', { name: 'コミュニティに参加する' })).toBeVisible()
    await other.goto(topicUrl)
    await expect(other.getByText(/返信できるのはコミュニティのメンバーだけです/)).toBeVisible()
    await other.close()

    // オーナーがコミュニティを削除すると一覧へ戻る
    await page.goto(communityUrl)
    await page.getByRole('button', { name: 'コミュニティを削除' }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click()
    await expect(page.getByRole('heading', { name: 'コミュニティ', level: 1 })).toBeVisible()
    await expect(page.getByRole('link', { name })).toHaveCount(0)
  })
})
