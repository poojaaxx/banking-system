import { expect, test } from '@playwright/test'
import { registerFundedCustomer } from './helpers'

test.describe('SPA routing and static asset serving', () => {
  test('unauthenticated direct navigation to a protected route redirects to login', async ({ page }) => {
    await page.goto('/accounts')
    await page.waitForURL('**/login')
    await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible()
  })

  test('hard refresh on an authenticated SPA route preserves the session', async ({ page }) => {
    await registerFundedCustomer(page, 'Frank Demo', '100')
    const detailUrl = page.url()
    await page.reload()
    await expect(page.getByText('Simulated deposit')).toBeVisible()
    expect(page.url()).toBe(detailUrl)
  })

  test('a missing static asset 404s instead of falling back to index.html', async ({ page }) => {
    // Vite's dev middleware has its own fallback behavior for unmatched paths;
    // this invariant is only meaningful against the packaged Spring Boot app,
    // which is what actually serves static assets in production. Run with
    // PLAYWRIGHT_BASE_URL pointed at the packaged image to exercise this.
    test.skip(process.env.E2E_TARGET !== 'packaged', 'Only meaningful against the packaged app, not the Vite dev server');
    const response = await page.goto('/assets/does-not-exist-12345.js')
    expect(response?.status()).toBe(404)
    const contentType = response?.headers()['content-type'] ?? ''
    expect(contentType).not.toContain('text/html')
  })

  test('an API error never becomes an SPA success response', async ({ page }) => {
    const response = await page.request.get('/api/customer/accounts/999999999')
    expect(response.status()).toBeGreaterThanOrEqual(400)
    const contentType = response.headers()['content-type'] ?? ''
    expect(contentType).toContain('application/json')
  })
})
