import { expect, test } from '@playwright/test'
import { apiRegisterCustomer, apiSend, csrfToken } from './helpers'

/**
 * Error-handling contract for the JSON API, checked against the packaged app:
 * authentication -> authorization -> CSRF are enforced first; only then does
 * routing decide between 404 (no such route) and 405 + Allow (route exists,
 * wrong method). No API response may ever be SPA HTML or an accidental 500.
 */
test.describe('API error contract', () => {
  test('unauthenticated requests to unknown API routes are 401, not 404 or HTML', async ({ request }) => {
    const response = await request.get('/api/customer/does-not-exist')
    expect(response.status()).toBe(401)
    expect(response.headers()['content-type']).toContain('application/json')
  })

  test('authenticated: unknown routes are 404 JSON for every method', async ({ request }) => {
    await apiRegisterCustomer(request)

    for (const method of ['get', 'post', 'put', 'patch', 'delete'] as const) {
      const response = method === 'get'
        ? await request.get('/api/customer/does-not-exist')
        : await apiSend(request, method, '/api/customer/does-not-exist', {})
      expect(response.status(), `${method} unknown route`).toBe(404)
      expect(response.headers()['content-type']).toContain('application/json')
      expect(await response.text()).not.toContain('<html')
    }
  })

  test('CSRF is enforced before routing: unknown-route POST without a token is 403', async ({ request }) => {
    await apiRegisterCustomer(request)
    const response = await request.post('/api/customer/does-not-exist', { data: {} })
    expect(response.status()).toBe(403)
  })

  test('authorization is enforced before routing: a customer probing admin routes gets 403, never 404', async ({ request }) => {
    await apiRegisterCustomer(request)
    const response = await request.get('/api/admin/does-not-exist')
    expect(response.status()).toBe(403)
  })

  test('existing route with an unsupported method is 405 with an Allow header', async ({ request }) => {
    await apiRegisterCustomer(request)

    const get = await request.get('/api/customer/deposits')
    expect(get.status()).toBe(405)
    expect(get.headers()['allow']).toContain('POST')
    expect(get.headers()['content-type']).toContain('application/json')

    const put = await apiSend(request, 'put', '/api/customer/transfers', {})
    expect(put.status()).toBe(405)
    expect(put.headers()['allow']).toContain('POST')

    const del = await apiSend(request, 'delete', '/api/customer/deposits')
    expect(del.status()).toBe(405)
  })

  test('malformed JSON is a 400, not a 500', async ({ request }) => {
    await apiRegisterCustomer(request)
    const response = await request.post('/api/customer/deposits', {
      headers: { 'X-XSRF-TOKEN': await csrfToken(request), 'Content-Type': 'application/json', 'Idempotency-Key': 'malformed-json-probe' },
      data: '{"accountId": 1, "amount": ',
    })
    expect(response.status()).toBe(400)
  })

  test('missing required Idempotency-Key header is a 400, not a 500', async ({ request }) => {
    await apiRegisterCustomer(request)
    const response = await apiSend(request, 'post', '/api/customer/deposits', { accountId: 1, amount: '10.00' })
    expect(response.status()).toBe(400)
  })

  test('SPA client routes still get index.html, but only outside /api and /actuator', async ({ request }) => {
    const spa = await request.get('/some/client/route')
    expect(spa.status()).toBe(200)
    expect(spa.headers()['content-type']).toContain('text/html')

    const api = await request.get('/api/some/client/route')
    expect(api.headers()['content-type'] ?? '').not.toContain('text/html')
  })
})
