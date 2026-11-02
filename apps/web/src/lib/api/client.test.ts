import { describe, expect, it, vi } from 'vitest'
import { ApiError, createApiClient, joinUrl } from './client'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('joinUrl', () => {
  it.each([
    ['http://localhost:8080', '/actuator/health'],
    ['http://localhost:8080/', '/actuator/health'],
    ['http://localhost:8080/', 'actuator/health'],
  ])('joins %s and %s with exactly one slash', (baseUrl, path) => {
    expect(joinUrl(baseUrl, path)).toBe('http://localhost:8080/actuator/health')
  })

  it('keeps a path prefix on the base URL', () => {
    expect(joinUrl('https://example.com/queryforge/', '/actuator/health')).toBe(
      'https://example.com/queryforge/actuator/health',
    )
  })
})

describe('createApiClient', () => {
  it('sends GET requests to the configured base URL and parses JSON', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse({ status: 'UP' }))
    const client = createApiClient({ baseUrl: 'http://api.test', fetch })

    await expect(client.get('/actuator/health')).resolves.toEqual({ status: 'UP' })
    expect(fetch).toHaveBeenCalledWith(
      'http://api.test/actuator/health',
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('parses vendor JSON media types such as Actuator responses', async () => {
    const response = new Response('{"status":"UP"}', {
      headers: { 'Content-Type': 'application/vnd.spring-boot.actuator.v3+json' },
    })
    const client = createApiClient({ baseUrl: 'http://api.test', fetch: vi.fn<typeof globalThis.fetch>().mockResolvedValue(response) })

    await expect(client.get('/actuator/health')).resolves.toEqual({ status: 'UP' })
  })

  it('throws ApiError with the status and parsed body for non-2xx responses', async () => {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(jsonResponse({ error: 'Not Found' }, 404))
    const client = createApiClient({ baseUrl: 'http://api.test', fetch })

    const error: unknown = await client.get('/missing').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, body: { error: 'Not Found' } })
  })

  it('aborts requests that exceed the timeout', async () => {
    const neverResolves = vi.fn<typeof globalThis.fetch>(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new Error('aborted', { cause: init.signal?.reason })))
        }),
    )
    const client = createApiClient({ baseUrl: 'http://api.test', fetch: neverResolves, timeoutMs: 10 })

    await expect(client.get('/slow')).rejects.toMatchObject({ cause: { name: 'TimeoutError' } })
  })
})
