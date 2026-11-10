import { expect, test, type Page } from '@playwright/test'

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`
}

async function signup(page: Page) {
  const suffix = uniqueSuffix()
  const user = { username: `e2e_${suffix}`.slice(0, 30), displayName: `旅人${suffix.slice(-4)}` }
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

test.describe('スポット投稿', () => {
  test('投稿 → 詳細 → 編集 → 新着一覧に表示 → 削除', async ({ page }) => {
    await signup(page)
    const title = `E2E 清水寺 ${uniqueSuffix()}`

    // 投稿する
    await page.getByRole('link', { name: '投稿する' }).click()
    await page.getByLabel('タイトル').fill(title)
    await page.getByLabel('場所').fill('清水寺（京都府京都市東山区）')
    await page.getByText('関西').click()
    await page.getByRole('checkbox', { name: '京都' }).check()
    await page.getByLabel('感想（任意）').fill('開門直後に行くと人が少なくて最高でした。')
    await page.getByRole('button', { name: '投稿する' }).click()

    // 詳細画面
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await expect(page.getByText('開門直後に行くと人が少なくて最高でした。')).toBeVisible()
    const detailTags = page.getByRole('list', { name: 'エリアタグ', exact: true })
    await expect(detailTags.getByText('京都')).toBeVisible()

    // 編集する（エリアタグを追加）
    await page.getByRole('link', { name: '編集' }).click()
    await expect(page.getByLabel('タイトル')).toHaveValue(title)
    await page.getByText('関西').click()
    await page.getByRole('checkbox', { name: '大阪' }).check()
    await page.getByRole('button', { name: '保存する' }).click()
    // 詳細画面に戻るのを待つ
    await expect(page).toHaveURL(/\/spots\/\d+$/)
    await expect(detailTags.getByText('大阪')).toBeVisible()

    // 新着一覧（トップ）に表示される
    await page.getByRole('link', { name: 'TripDiary' }).click()
    await expect(
      page.getByRole('list', { name: '新着スポット' }).getByRole('link', { name: title }),
    ).toBeVisible()

    // 削除する
    await page.getByRole('link', { name: title }).click()
    await page.getByRole('button', { name: '削除' }).click()
    await page.getByRole('alertdialog').getByRole('button', { name: '削除する' }).click()
    await expect(page.getByRole('heading', { name: /の投稿$/ })).toBeVisible()
    await expect(page.getByRole('link', { name: title })).toHaveCount(0)
  })

  test('非公開のスポットは本人だけが見られ、再読み込みしても表示できる', async ({ page, browser }) => {
    const owner = await signup(page)
    const title = `E2E 非公開 ${uniqueSuffix()}`

    await page.goto('/spots/new')
    await page.getByLabel('タイトル').fill(title)
    await page.getByLabel('場所').fill('自宅の近く')
    await page.getByText('九州・沖縄').click()
    await page.getByRole('checkbox', { name: '沖縄' }).check()
    await page.getByLabel('非公開（自分だけ）').check()
    await page.getByRole('button', { name: '投稿する' }).click()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    const spotUrl = page.url()

    // 本人は再読み込みしても見られる（ログイン状態の復元を待ってから取得する）
    await page.reload()
    await expect(page.getByRole('heading', { name: title })).toBeVisible()

    // 未ログインの別ブラウザからは見えない
    const guest = await browser.newPage()
    await guest.goto(spotUrl)
    await expect(guest.getByRole('heading', { name: 'スポットが見つかりません' })).toBeVisible()
    await guest.goto(`/users/${owner.username}`)
    await expect(guest.getByText('まだ投稿がありません。')).toBeVisible()
    await guest.close()
  })
})
