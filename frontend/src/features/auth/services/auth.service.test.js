import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()
vi.mock('../../../lib/api-client.js', () => ({
  apiClient: {},
  authApiClient: { post: (...args) => post(...args) },
}))

import { refreshTokenRequest } from './auth.service.js'

describe('refreshTokenRequest (BUG-055)', () => {
  beforeEach(() => post.mockReset())

  it.each([401, 403])('reports a rejected session on %i', async (status) => {
    post.mockRejectedValueOnce(Object.assign(new Error('no'), { status }))

    await expect(refreshTokenRequest({ rethrowTransient: true })).resolves.toBe(false)
  })

  it.each([
    ['network error', new Error('Failed to fetch')],
    ['timeout', Object.assign(new Error('Request timeout'), { code: 'TIMEOUT' })],
    ['502 during a deploy', Object.assign(new Error('Bad gateway'), { status: 502 })],
  ])('throws a transient error on %s when asked to', async (_name, error) => {
    post.mockRejectedValueOnce(error)

    await expect(refreshTokenRequest({ rethrowTransient: true })).rejects.toMatchObject({ transient: true })
  })

  it('keeps returning false for the callers that do not ask for transient errors', async () => {
    post.mockRejectedValueOnce(new Error('Failed to fetch'))

    await expect(refreshTokenRequest()).resolves.toBe(false)
  })
})
