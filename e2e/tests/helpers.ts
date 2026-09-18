import { expect, type Page } from '@playwright/test'

export function uniqueSuffix(): string {
  return Math.random().toString(36).slice(2, 10)
}

export interface RegisteredCustomer {
  username: string
  password: string
  accountNumber: string
}

/** Registers a fresh customer, opens one account, and deposits the given amount. Leaves the page on the account detail view. */
export async function registerFundedCustomer(page: Page, fullName: string, fundAmount: string): Promise<RegisteredCustomer> {
  const username = 'e2e_' + uniqueSuffix()
  const password = 'correct-horse-battery-staple'

  await page.goto('/')
  await page.waitForURL('**/login')
  await page.click('text=Create a new account')
  await page.waitForURL('**/register')
  await page.fill('#fullName', fullName)
  await page.fill('#email', `e2e_${uniqueSuffix()}@example.invalid`)
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type=submit]')

  await expect(page.getByText('Save your recovery codes')).toBeVisible()
  await page.click('text=continue')
  await page.waitForURL('**/dashboard')

  await page.click('text=Manage accounts')
  await page.waitForURL('**/accounts')
  await page.click('text=Open new account')
  await page.fill('#nickname', 'Main')
  await page.click('button:has-text("Create account")')
  await expect(page.locator('.account-card')).toBeVisible()
  await page.click('.account-card')
  await expect(page.getByText('Simulated deposit')).toBeVisible()

  const accountNumber = (await page.locator('.account-number').first().textContent())!.trim()

  await page.click('button:has-text("Simulated deposit")')
  await page.fill('#deposit-amount', fundAmount)
  await page.click('button:has-text("Confirm deposit")')
  await expect(page.getByText('Deposit of')).toBeVisible({ timeout: 10_000 })
  await page.click('button:has-text("Close")')

  return { username, password, accountNumber }
}

export function readCookie(cookies: { name: string; value: string }[], name: string): string | undefined {
  return cookies.find((c) => c.name === name)?.value
}
