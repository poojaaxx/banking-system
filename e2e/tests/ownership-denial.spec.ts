import { expect, test } from '@playwright/test'
import { registerFundedCustomer } from './helpers'

test('a customer cannot read another customer\'s account by guessing its id', async ({ browser }) => {
  const contextA = await browser.newContext()
  const contextB = await browser.newContext()
  const pageA = await contextA.newPage()
  const pageB = await contextB.newPage()

  await registerFundedCustomer(pageA, 'Grace Demo', '100')
  await registerFundedCustomer(pageB, 'Heidi Demo', '100')

  const bAccountId = Number(new URL(pageB.url()).pathname.split('/').pop())

  // A is authenticated (has a valid session + CSRF), but tries B's account id.
  const response = await pageA.request.get(`/api/customer/accounts/${bAccountId}`)
  expect(response.status()).toBe(403)

  await contextA.close()
  await contextB.close()
})

test('admin routes reject a customer session', async ({ page }) => {
  await registerFundedCustomer(page, 'Ivan Demo', '100')
  const response = await page.request.get('/api/admin/dashboard')
  expect(response.status()).toBe(403)
})
