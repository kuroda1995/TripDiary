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

/** 位置情報の代わりの目印（アップロードされたファイルに残っていないことを確かめる）。 */
const SECRET = 'GPS-SECRET-35.0116N-135.7681E'

/** Orientation と、目印を入れた ImageDescription を持つ EXIF（APP1 セグメント）。 */
function exifSegment(orientation: number): Buffer {
  const description = Buffer.from(`${SECRET}\0`, 'latin1')
  const tiff = Buffer.alloc(8 + 2 + 24 + 4)
  tiff.write('MM', 0, 'latin1')
  tiff.writeUInt16BE(42, 2)
  tiff.writeUInt32BE(8, 4)
  tiff.writeUInt16BE(2, 8)
  // Orientation（SHORT × 1）
  tiff.writeUInt16BE(0x0112, 10)
  tiff.writeUInt16BE(3, 12)
  tiff.writeUInt32BE(1, 14)
  tiff.writeUInt16BE(orientation, 18)
  // ImageDescription（ASCII。本体は IFD の後ろ）
  tiff.writeUInt16BE(0x010e, 22)
  tiff.writeUInt16BE(2, 24)
  tiff.writeUInt32BE(description.length, 26)
  tiff.writeUInt32BE(tiff.length, 30)
  const payload = Buffer.concat([Buffer.from('Exif\0\0', 'latin1'), tiff, description])
  const header = Buffer.from([0xff, 0xe1, 0, 0])
  header.writeUInt16BE(payload.length + 2, 2)
  return Buffer.concat([header, payload])
}

test('写真の位置情報などのメタデータは取り除いてアップロードし、向きは保つ', async ({ page, request }) => {
  await signup(page)
  const title = `E2E EXIF ${uniqueSuffix()}`

  // 横4×縦2 の JPEG をブラウザで作り、「90度回転して表示」の向きと目印入りの EXIF を付ける
  const base64 = await page.evaluate<string>(`(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 4
    canvas.height = 2
    const context = canvas.getContext('2d')
    context.fillStyle = '#c00'
    context.fillRect(0, 0, 4, 2)
    return canvas.toDataURL('image/jpeg', 0.9).split(',')[1]
  })()`)
  const plain = Buffer.from(base64, 'base64')
  const withExif = Buffer.concat([plain.subarray(0, 2), exifSegment(6), plain.subarray(2)])
  expect(withExif.includes(SECRET)).toBe(true)

  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('どこか')
  await page.getByText('関西').click()
  await page.getByRole('checkbox', { name: '京都' }).check()
  await page
    .getByLabel('写真を追加')
    .setInputFiles({ name: 'gps.jpg', mimeType: 'image/jpeg', buffer: withExif })
  await expect(page.getByRole('progressbar')).toHaveCount(0)
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()

  // S3 に保存されたファイルに目印が残っていない
  const image = page.getByRole('img', { name: `${title}の写真（1/1枚目）` })
  const src = await image.getAttribute('src')
  const stored = await (await request.get(src!)).body()
  expect(stored.includes(SECRET)).toBe(false)
  expect(stored.length).toBeLessThan(withExif.length)

  // 向きは保たれている（90度回転して縦2×横4 → 表示は 幅2・高さ4）
  await expect
    .poll(() =>
      image.evaluate((img) => {
        const loaded = img as unknown as LoadedImage & { naturalHeight: number }
        return loaded.complete ? `${loaded.naturalWidth}x${loaded.naturalHeight}` : ''
      }),
    )
    .toBe('2x4')
})
