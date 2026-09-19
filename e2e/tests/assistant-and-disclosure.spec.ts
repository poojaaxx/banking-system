import { expect, test } from '@playwright/test'
import { apiSend, registerFundedCustomer } from './helpers'

test.describe('AI stays optional; fictional-funds disclosure stays discreet', () => {
  test('without a Groq key the assistant answers with a labelled, calculated fallback and figures from the backend', async ({ page }) => {
    await registerFundedCustomer(page, 'Fallback Fran', '5000')
    const accountId = Number(new URL(page.url()).pathname.split('/').pop())
    const withdrawal = await apiSend(page.request, 'post', '/api/customer/withdrawals',
      { accountId, amount: '250.00', description: 'Uber ride home' }, { 'Idempotency-Key': crypto.randomUUID() })
    expect(withdrawal.status()).toBe(200)

    const status = await (await page.request.get('/api/customer/ai/status')).json()
    expect(status.aiAvailable).toBe(false)

    await page.goto('/assistant')
    await expect(page.getByText(/AI-written answers are currently unavailable/)).toBeVisible()

    await page.click('button:has-text("How much did I spend this month?")')
    await expect(page.getByText('Calculated answer', { exact: true })).toBeVisible()
    await expect(page.getByText(/AI is not available right now/)).toBeVisible()
    await expect(page.getByText(/You spent ₹250\.00 so far this month/)).toBeVisible()
    await expect(page.getByLabel('Figures from your records')).toContainText('₹250.00')
    await expect(page.getByText(/AI answer/)).toHaveCount(0)

    await page.click('button:has-text("Show my largest payments.")')
    await expect(page.getByText(/Uber ride home/).first()).toBeVisible()
  })

  test('on-demand AI category suggestion degrades to a clear message while banking keeps working', async ({ page }) => {
    await registerFundedCustomer(page, 'Category Cara', '5000')
    const accountId = Number(new URL(page.url()).pathname.split('/').pop())
    await apiSend(page.request, 'post', '/api/customer/withdrawals',
      { accountId, amount: '120.00', description: 'Uber ride to office' }, { 'Idempotency-Key': crypto.randomUUID() })

    await page.goto(`/accounts/${accountId}`)
    await expect(page.getByText('Suggested: Transport')).toBeVisible()
    await page.click('button:has-text("Ask AI")')
    await expect(page.getByText('AI suggestion unavailable right now.')).toBeVisible()
    await page.click('button:has-text("Accept")')
    await expect(page.getByRole('button', { name: 'Transport' })).toBeVisible()
  })

  test('the fictional-funds note is a quiet line on login, with an About page, and no dashboard banner', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByText(/SecureBank uses fictional funds only/)).toBeVisible()
    await expect(page.locator('.demo-banner')).toHaveCount(0)

    await page.getByRole('link', { name: 'About' }).click()
    await page.waitForURL('**/about')
    await expect(page.getByText(/All money in it is fictional/)).toBeVisible()

    await registerFundedCustomer(page, 'Brand Bea', '100')
    await page.goto('/dashboard')
    await expect(page.getByText('SecureBank').first()).toBeVisible()
    await expect(page.locator('body')).not.toContainText(/Demo banking|Demo Bank/)
    await expect(page.locator('.demo-banner')).toHaveCount(0)
  })
})
