import { expect, test } from '@playwright/test'

test.describe('スモークテスト', () => {
  test('トップページが表示され、バックエンド（API + DB）と疎通できる', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveTitle('TripDiary')
    await expect(page.getByRole('heading', { name: '旅の思い出を、みんなと。' })).toBeVisible()
    await expect(page.getByTestId('api-status')).toHaveText('API: 正常')
  })

  test('存在しない URL では 404 ページからトップへ戻れる', async ({ page }) => {
    await page.goto('/no-such-page')

    await expect(page.getByRole('heading', { name: 'ページが見つかりません' })).toBeVisible()
    await page.getByRole('link', { name: 'トップへ戻る' }).click()
    await expect(page).toHaveURL('/')
  })
})
