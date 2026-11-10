import { expect, test, type Page } from '@playwright/test'

/**
 * 写真アップロードの E2E（docs/qa_test_plan.md 4.2）。
 * 前提: バックエンド・DB に加えて S3Mock（:9090）が起動していること（docker compose / CI の services）。
 */

// 2x2 の赤い PNG
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEUlEQVR4nGP4z8DwnwGMgRQAH+4D/dJQfRoAAAAASUVORK5CYII=',
  'base64',
)

/** e2e の型設定には DOM の型が無いため、使うプロパティだけを定義する。 */
type LoadedImage = { complete: boolean; naturalWidth: number }

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

/** 画像が実際に読み込まれて表示できているか（S3 から配信されているか）。 */
async function expectImageLoaded(page: Page, name: string) {
  const image = page.getByRole('img', { name })
  await expect(image).toBeVisible()
  await expect
    .poll(() =>
      image.evaluate(
        (img) => (img as unknown as LoadedImage).complete && (img as unknown as LoadedImage).naturalWidth,
      ),
    )
    .toBe(2)
}

test('写真をアップロードして投稿すると、詳細画面と新着一覧に表示される', async ({ page }) => {
  await signup(page)
  const title = `E2E 写真 ${uniqueSuffix()}`

  await page.getByRole('link', { name: '投稿する' }).click()
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('伏見稲荷大社')
  await page.getByText('関西').click()
  await page.getByRole('checkbox', { name: '京都' }).check()

  // ブラウザから署名付き URL で S3（S3Mock）へ直接アップロードする
  await page.getByLabel('写真を追加').setInputFiles([
    { name: 'torii-1.png', mimeType: 'image/png', buffer: PNG },
    { name: 'torii-2.png', mimeType: 'image/png', buffer: PNG },
  ])
  const photos = page.getByRole('list', { name: '選択した写真' })
  await expect(photos.getByRole('listitem')).toHaveCount(2)
  await expect(page.getByRole('progressbar')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '投稿する' })).toBeEnabled()

  await page.getByRole('button', { name: '投稿する' }).click()

  // 詳細画面: 写真が S3 から配信され、切り替えられる
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expectImageLoaded(page, `${title}の写真（1/2枚目）`)
  await page.getByRole('button', { name: '次の写真' }).click()
  await expectImageLoaded(page, `${title}の写真（2/2枚目）`)

  // 編集で1枚外す
  await page.getByRole('link', { name: '編集' }).click()
  await expect(photos.getByRole('listitem')).toHaveCount(2)
  await page.getByRole('button', { name: '写真2を削除' }).click()
  await page.getByRole('button', { name: '保存する' }).click()
  await expect(page).toHaveURL(/\/spots\/\d+$/)
  await expectImageLoaded(page, `${title}の写真（1/1枚目）`)
  await expect(page.getByRole('button', { name: '次の写真' })).toHaveCount(0)

  // 新着一覧のカードにカバー写真が出る
  await page.getByRole('link', { name: 'TripDiary' }).click()
  const card = page
    .getByRole('list', { name: '新着スポット' })
    .getByRole('listitem')
    .filter({ hasText: title })
  await expect(card.locator('img')).toHaveCount(1)
  await expect
    .poll(() => card.locator('img').evaluate((img) => (img as unknown as LoadedImage).naturalWidth))
    .toBe(2)
})
