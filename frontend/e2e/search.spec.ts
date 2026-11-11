import { expect, test, type Page } from '@playwright/test'

/** エリアタグ・キーワード検索の E2E（docs/qa_test_plan.md 4.6）。 */

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

async function postSpot(page: Page, title: string, region: string, area: string) {
  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('どこか')
  await page.getByText(region).click()
  await page.getByRole('checkbox', { name: area }).check()
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
}

test('地方全体・キーワードで絞り込め、条件は URL に残り、エリアタグから検索できる', async ({ page }) => {
  await signup(page)
  // 他のテストの投稿と区別するため、キーワードに一意な文字列を使う
  const marker = `検索E2E${uniqueSuffix()}`
  const kyoto = `${marker} 京都の宿`
  const osaka = `${marker} 大阪の宿`
  const fukuoka = `${marker} 福岡の宿`
  await postSpot(page, kyoto, '関西', '京都')
  await postSpot(page, osaka, '関西', '大阪')
  await postSpot(page, fukuoka, '九州・沖縄', '福岡')

  // キーワードで検索
  await page.getByRole('link', { name: '探す', exact: true }).click()
  await page.getByRole('searchbox', { name: 'キーワード（タイトル・場所）' }).fill(marker)
  await page.getByRole('button', { name: '検索' }).click()
  const results = page.getByRole('list', { name: '検索結果' })
  await expect(results.getByRole('heading')).toHaveText([fukuoka, osaka, kyoto])

  // 関西全体で絞り込む（子タグの京都・大阪が含まれ、福岡は出ない）
  await page.getByText('関西', { exact: true }).click()
  // 選択状態は URL から決まるため、check() ではなくクリック後に反映を待つ
  await page.getByRole('checkbox', { name: '関西全体' }).click()
  await expect(page.getByRole('checkbox', { name: '関西全体' })).toBeChecked()
  await expect(results.getByRole('heading')).toHaveText([osaka, kyoto])
  await expect(page).toHaveURL(/areas=kansai/)

  // 再読み込みしても条件が残る
  await page.reload()
  await expect(page.getByRole('button', { name: '関西全体 を外す' })).toBeVisible()
  await expect(page.getByRole('list', { name: '検索結果' }).getByRole('heading')).toHaveText([osaka, kyoto])

  // 戻る操作で前の条件（キーワードのみ）に戻る
  await page.goBack()
  await expect(page.getByRole('list', { name: '検索結果' }).getByRole('heading')).toHaveText([
    fukuoka,
    osaka,
    kyoto,
  ])

  // 0件なら案内を出す
  await page.getByRole('searchbox', { name: 'キーワード（タイトル・場所）' }).fill(`${marker}存在しない`)
  await page.getByRole('button', { name: '検索' }).click()
  await expect(page.getByText('条件を変えて検索してください。', { exact: false })).toBeVisible()

  // スポット詳細のエリアタグから、そのタグで検索した結果へ
  await page.goto('/')
  await page.getByRole('link', { name: fukuoka }).click()
  await page.getByRole('link', { name: '福岡のスポットを探す' }).click()
  await expect(page).toHaveURL(/\/search\?areas=fukuoka$/)
  await expect(page.getByRole('link', { name: fukuoka })).toBeVisible()
  await expect(page.getByRole('link', { name: kyoto })).toHaveCount(0)
})
