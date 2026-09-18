import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from './client'

function mockFetchOnce(response: Partial<Response> & { jsonBody?: unknown }) {
  const { jsonBody, ...rest } = response
  const fakeResponse = {
    ok: rest.status === undefined || (rest.status >= 200 && rest.status < 300),
    status: 200,
    statusText: 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: async () => jsonBody ?? null,
    ...rest,
  } as Response
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse))
  return fakeResponse
}

describe('apiFetch', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('does not attach an X-XSRF-TOKEN header on a GET request', async () => {
    mockFetchOnce({ jsonBody: { ok: true } })
    document.cookie = 'XSRF-TOKEN=test-token; path=/'

    await apiFetch('/api/customer/accounts')

    const [, options] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(options.headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('attaches the X-XSRF-TOKEN header (read from the cookie) on a POST request', async () => {
    mockFetchOnce({ jsonBody: { ok: true } })
    document.cookie = 'XSRF-TOKEN=test-token; path=/'

    await apiFetch('/api/customer/transfers', { method: 'POST', body: { amount: '10' } })

    const [, options] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(options.headers['X-XSRF-TOKEN']).toBe('test-token')
    expect(options.credentials).toBe('include')
  })

  it('attaches the Idempotency-Key header when provided', async () => {
    mockFetchOnce({ jsonBody: { ok: true } })

    await apiFetch('/api/customer/transfers', { method: 'POST', body: {}, idempotencyKey: 'abc-123' })

    const [, options] = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]
    expect(options.headers['Idempotency-Key']).toBe('abc-123')
  })

  it('throws an ApiError with the parsed error body on a non-2xx response', async () => {
    mockFetchOnce({
      ok: false,
      status: 400,
      jsonBody: { timestamp: 't', status: 400, error: 'Bad Request', message: 'Amount must be positive', path: '/x' },
    })

    await expect(apiFetch('/api/customer/deposits', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 400,
      message: 'Amount must be positive',
    })
  })

  it('rejects with an instance of ApiError specifically', async () => {
    mockFetchOnce({ ok: false, status: 500, jsonBody: null, headers: new Headers() })
    await expect(apiFetch('/api/x')).rejects.toBeInstanceOf(ApiError)
  })

  it('returns undefined for a 204 No Content response without attempting to parse JSON', async () => {
    mockFetchOnce({ status: 204, headers: new Headers() })
    const result = await apiFetch('/api/customer/accounts/1/close', { method: 'POST' })
    expect(result).toBeUndefined()
  })
})
