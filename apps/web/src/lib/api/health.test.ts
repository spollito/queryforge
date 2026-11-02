import { describe, expect, it, vi } from 'vitest'
import { ApiError, createApiClient } from './client'
import { fetchHealth } from './health'

function clientResponding(body: string, status: number, contentType = 'application/json') {
  const fetch = vi
    .fn<typeof globalThis.fetch>()
    .mockResolvedValue(new Response(body, { status, headers: { 'Content-Type': contentType } }))
  return createApiClient({ baseUrl: 'http://api.test', fetch })
}

describe('fetchHealth', () => {
  it('returns the report when every component is up', async () => {
    const client = clientResponding('{"status":"UP","components":{"db":{"status":"UP"}}}', 200)

    await expect(fetchHealth(client)).resolves.toEqual({
      status: 'UP',
      components: { db: { status: 'UP' } },
    })
  })

  it('returns the report from a 503 so a down database stays visible', async () => {
    const client = clientResponding('{"status":"DOWN","components":{"db":{"status":"DOWN"}}}', 503)

    await expect(fetchHealth(client)).resolves.toMatchObject({ components: { db: { status: 'DOWN' } } })
  })

  it('rethrows failures that do not carry a health report', async () => {
    const client = clientResponding('Bad Gateway', 502, 'text/plain')

    await expect(fetchHealth(client)).rejects.toBeInstanceOf(ApiError)
  })
})
