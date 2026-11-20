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

test.describe('お寺の評価・特徴タグ', () => {
  test('評価と特徴タグを付けて投稿 → 詳細に表示 → 特徴タグで絞り込み', async ({ page }) => {
    await signup(page)
    const title = `E2E 瑞龍寺 ${uniqueSuffix()}`

    await page.getByRole('link', { name: '投稿する' }).click()
    await page.getByLabel('タイトル').fill(title)
    await page.getByLabel('場所').fill('高岡山 瑞龍寺（富山県高岡市）')
    await page.getByText('中部').click()
    await page.getByRole('checkbox', { name: '富山' }).check()
    await page.getByRole('radiogroup', { name: '静寂さ・雰囲気' }).getByRole('radio', { name: '5点' }).check()
    await page
      .getByRole('radiogroup', { name: '庭園・建築の美しさ' })
      .getByRole('radio', { name: '4点' })
      .check()
    await page.getByRole('checkbox', { name: '御朱印あり' }).check()
    await page.getByRole('checkbox', { name: '国宝・重要文化財' }).check()
    await page.getByRole('button', { name: '投稿する' }).click()

    // 詳細画面に評価・特徴タグが表示される
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    const ratings = page.getByRole('region', { name: 'お寺の評価' })
    await expect(ratings.getByRole('img', { name: '5段階中5' })).toBeVisible()
    await expect(ratings.getByRole('img', { name: '5段階中4' })).toBeVisible()
    const features = page.getByRole('list', { name: 'お寺の特徴' })
    await expect(features.getByText('国宝・重要文化財')).toBeVisible()

    // 特徴タグから検索すると、そのタグで絞り込まれる
    await features.getByRole('link', { name: '「御朱印あり」のお寺を探す' }).click()
    await expect(page).toHaveURL(/features=GOSHUIN/)
    await expect(page.getByRole('checkbox', { name: '御朱印あり' })).toBeChecked()
    await expect(page.getByRole('link', { name: title })).toBeVisible()

    // タグを足して条件に合わなくなると表示されない（AND 条件）
    // チェック状態は URL から作るため（反映が非同期）、check() ではなく click() して状態を待つ
    await page.getByRole('checkbox', { name: '駐車場あり' }).click()
    await expect(page.getByRole('checkbox', { name: '駐車場あり' })).toBeChecked()
    await expect(page).toHaveURL(/features=GOSHUIN%2CPARKING|features=GOSHUIN,PARKING/)
    await expect(page.getByRole('link', { name: title })).toHaveCount(0)
  })
})
