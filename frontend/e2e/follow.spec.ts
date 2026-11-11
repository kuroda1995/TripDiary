import { expect, test, type Page } from '@playwright/test'

/** フォロー / フォロワー・タイムラインの E2E（docs/qa_test_plan.md 4.5）。 */

function uniqueSuffix() {
  return `${Date.now()}${Math.floor(Math.random() * 1000)}`
}

async function signup(page: Page) {
  const suffix = uniqueSuffix()
  const username = `e2e_${suffix}`.slice(0, 30)
  const displayName = `旅人${suffix.slice(-4)}`
  await page.goto('/signup')
  await page.getByLabel('メールアドレス').fill(`e2e_${suffix}@example.com`)
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('表示名').fill(displayName)
  await page.getByLabel('パスワード', { exact: true }).fill('Passw0rd123')
  await page.getByLabel('パスワード（確認）').fill('Passw0rd123')
  await page.getByRole('button', { name: '登録する' }).click()
  await expect(page.getByRole('link', { name: displayName })).toBeVisible()
  return { username, displayName }
}

async function postSpot(page: Page, title: string, visibility: 'PUBLIC' | 'PRIVATE' = 'PUBLIC') {
  await page.goto('/spots/new')
  await page.getByLabel('タイトル').fill(title)
  await page.getByLabel('場所').fill('中洲')
  await page.getByText('九州・沖縄').click()
  await page.getByRole('checkbox', { name: '福岡' }).check()
  if (visibility === 'PRIVATE') await page.getByLabel('非公開（自分だけ）').check()
  await page.getByRole('button', { name: '投稿する' }).click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
}

test('フォローすると件数が増えて一覧に載り、タイムラインに公開投稿だけが出る。解除すると元に戻る', async ({
  page,
  browser,
}) => {
  // フォローされる側: 公開と非公開を1件ずつ投稿
  const author = await signup(page)
  const publicTitle = `E2E 公開 ${uniqueSuffix()}`
  const privateTitle = `E2E 非公開 ${uniqueSuffix()}`
  await postSpot(page, publicTitle)
  await postSpot(page, privateTitle, 'PRIVATE')

  // フォローする側
  const other = await browser.newPage()
  const follower = await signup(other)
  await other.goto('/timeline')
  await expect(
    other.getByText('フォロー中のユーザーの投稿がここに表示されます。', { exact: false }),
  ).toBeVisible()

  await other.goto(`/users/${author.username}`)
  await expect(other.getByRole('heading', { level: 1, name: author.displayName })).toBeVisible()
  const counts = other.getByRole('list', { name: '件数' })
  // 他人からは公開投稿だけが数えられる
  await expect(counts).toContainText('1 投稿')
  await expect(counts).toContainText('0 フォロワー')

  await other.getByRole('button', { name: `@${author.username} をフォローする` }).click()
  const unfollow = other.getByRole('button', { name: `@${author.username} のフォローを解除` })
  // カーソルを外すと「フォロー中」、乗せると「フォロー解除」と表示する
  await other.mouse.move(0, 0)
  await expect(unfollow.getByText('フォロー中')).toBeVisible()
  await expect(unfollow.getByText('フォロー解除')).toBeHidden()
  await unfollow.hover()
  await expect(unfollow.getByText('フォロー解除')).toBeVisible()
  await expect(unfollow.getByText('フォロー中')).toBeHidden()
  await expect(counts).toContainText('1 フォロワー')

  // 再読み込みしても保持されている
  await other.reload()
  await expect(other.getByRole('button', { name: `@${author.username} のフォローを解除` })).toBeVisible()

  // フォロワー一覧に載る
  await other.getByRole('link', { name: /フォロワー/ }).click()
  await expect(other.getByRole('list', { name: `@${author.username} のフォロワー` })).toContainText(
    follower.displayName,
  )

  // タイムラインには公開投稿だけが出る
  await other.getByRole('link', { name: 'タイムライン' }).click()
  await expect(other.getByRole('link', { name: publicTitle })).toBeVisible()
  await expect(other.getByRole('link', { name: privateTitle })).toHaveCount(0)

  // 本人のプロフィールは「プロフィール編集」と非公開も含めた件数
  await page.goto(`/users/${author.username}`)
  await expect(page.getByRole('link', { name: 'プロフィール編集' })).toBeVisible()
  await expect(page.getByRole('list', { name: '件数' })).toContainText('2 投稿')
  await expect(page.getByRole('list', { name: '件数' })).toContainText('1 フォロワー')

  // 解除すると元に戻る
  await other.goto(`/users/${author.username}`)
  await other.getByRole('button', { name: `@${author.username} のフォローを解除` }).click()
  await expect(other.getByRole('button', { name: `@${author.username} をフォローする` })).toBeVisible()
  await expect(counts).toContainText('0 フォロワー')
  await other.goto('/timeline')
  await expect(other.getByRole('link', { name: publicTitle })).toHaveCount(0)
})
