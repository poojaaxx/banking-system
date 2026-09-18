import { expect, test } from '@playwright/test'
import { registerFundedCustomer } from './helpers'

test.describe('Core money-movement flow', () => {
  test('two customers, a transfer, and a live update on the recipient side', async ({ browser }) => {
    const contextA = await browser.newContext()
    const contextB = await browser.newContext()
    const pageA = await contextA.newPage()
    const pageB = await contextB.newPage()

    const a = await registerFundedCustomer(pageA, 'Alice Demo', '10000')
    const b = await registerFundedCustomer(pageB, 'Bob Demo', '2000')

    // B watches their dashboard live -- no manual refresh from here on.
    await pageB.goto('/dashboard')
    await expect(pageB.getByText('₹2,000.00').first()).toBeVisible()

    await pageA.goto('/transfer')
    await pageA.fill('#destination', b.accountNumber)
    await pageA.fill('#amount', '3000')
    await pageA.fill('#description', 'Rent share')
    await pageA.click('button:has-text("Review transfer")')
    await expect(pageA.getByText('Confirm transfer')).toBeVisible()
    await pageA.click('button:has-text("Confirm & send")')

    await expect(pageA.getByText('Transfer completed.')).toBeVisible()
    await expect(pageA.getByText('₹7,000.00')).toBeVisible()

    // B's balance updates live via SSE, without a page reload.
    await expect(pageB.getByText('₹5,000.00').first()).toBeVisible({ timeout: 15_000 })
    await expect(pageB.getByText('1 unread')).toBeVisible()

    await pageB.goto('/notifications')
    await expect(pageB.getByText('Money received')).toBeVisible()

    await contextA.close()
    await contextB.close()
  })

  test('excessive withdrawal is rejected and balance is unchanged', async ({ page }) => {
    await registerFundedCustomer(page, 'Carol Demo', '500')
    await page.click('button:has-text("Simulated withdrawal")')
    await page.fill('#withdrawal-amount', '999999')
    await page.click('button:has-text("Confirm withdrawal")')
    await expect(page.getByText(/Insufficient funds|demo limits/i)).toBeVisible()
    await expect(page.getByText('₹500.00').first()).toBeVisible()
  })
})
