import { expect, test } from '@playwright/test'
import { registerFundedCustomer } from './helpers'

/**
 * Drives the real CSRF-protected API from inside the authenticated page
 * context (so cookies + the XSRF-TOKEN header behave exactly as they do for
 * the real app), firing several concurrent requests with the SAME
 * Idempotency-Key to prove the backend's DB-constraint-based dedup -- not
 * just client-side button-disabling -- is what prevents a double transfer.
 */
test('concurrent duplicate requests with the same idempotency key never move money twice', async ({ browser }) => {
  const senderContext = await browser.newContext()
  const recipientContext = await browser.newContext()
  const senderPage = await senderContext.newPage()
  const recipientPage = await recipientContext.newPage()

  const sender = await registerFundedCustomer(senderPage, 'Dana Demo', '10000')
  const recipient = await registerFundedCustomer(recipientPage, 'Eve Demo', '100')
  void sender

  await senderPage.goto('/accounts')
  await senderPage.click('.account-card')
  const url = new URL(senderPage.url())
  const sourceAccountId = Number(url.pathname.split('/').pop())

  const results = await senderPage.evaluate(
    async ({ sourceAccountId, destinationAccountNumber }) => {
      function readCookie(name: string) {
        const m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
        return m ? decodeURIComponent(m[1]) : ''
      }
      const key = crypto.randomUUID()
      const body = JSON.stringify({ sourceAccountId, destinationAccountNumber, amount: '500.00', description: 'dup test' })
      const send = () =>
        fetch('/api/customer/transfers', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': key,
            'X-XSRF-TOKEN': readCookie('XSRF-TOKEN'),
          },
          body,
        }).then((r) => r.status)
      return Promise.all([send(), send(), send()])
    },
    { sourceAccountId, destinationAccountNumber: recipient.accountNumber },
  )

  // Exactly one request should succeed (200); the rest are safely rejected
  // (409 conflict) by the database unique constraint, never silently retried
  // into a second transfer.
  expect(results.filter((s) => s === 200).length).toBe(1)

  await senderPage.goto(`/accounts/${sourceAccountId}`)
  await expect(senderPage.getByText('₹9,500.00').first()).toBeVisible()

  await recipientPage.goto('/dashboard')
  await expect(recipientPage.getByText('₹600.00').first()).toBeVisible()

  await senderContext.close()
  await recipientContext.close()
})
