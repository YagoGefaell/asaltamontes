import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClient } from './api-client.js'
import { clearAccessToken, setAccessToken } from '../features/auth/services/auth-token.js'
import { refreshTokenRequest } from '../features/auth/services/auth.service.js'

vi.mock('../features/auth/services/auth.service.js', () => ({
  refreshTokenRequest: vi.fn(),
}))

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('ApiClient', () => {
  let fetchMock
  let client

  beforeEach(() => {
    fetchMock = vi.fn()
    refreshTokenRequest.mockReset()
    vi.stubGlobal('fetch', fetchMock)
    clearAccessToken()
    client = new ApiClient(true, 'http://api.test')
  })

  it('returns the parsed JSON body and sends the bearer token', async () => {
    setAccessToken('token-123')
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { username: 'ana' }))

    const data = await client.get('/api/users/me')

    expect(data).toEqual({ username: 'ana' })
    const [url, config] = fetchMock.mock.calls[0]
    expect(url).toBe('http://api.test/api/users/me')
    expect(config.headers.Authorization).toBe('Bearer token-123')
  })

  it('does not send the bearer token to login', async () => {
    setAccessToken('token-123')
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { accessToken: 'new' }))

    await client.post('/auth/login', { username: 'ana', password: 'x' })

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('turns a ProblemDetail error into an Error with message, status and field errors', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        title: 'Conflict',
        detail: 'Username ya registrado',
        errors: { username: 'Username ya registrado' },
      }),
    )

    await expect(client.post('/auth/register', {})).rejects.toMatchObject({
      message: 'Username ya registrado',
      status: 409,
      errors: { username: 'Username ya registrado' },
    })
  })

  it('refreshes the token after a 401 and retries the request once', async () => {
    refreshTokenRequest.mockResolvedValueOnce(true)
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { detail: 'Token expirado' }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

    const data = await client.get('/api/users/me')

    expect(data).toEqual({ ok: true })
    expect(refreshTokenRequest).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not refresh on 403: it is a permission error, not an expired session', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { detail: 'No tienes permiso para acceder a este chat' }))

    await expect(client.get('/api/conversations/1/messages')).rejects.toMatchObject({
      message: 'No tienes permiso para acceder a este chat',
      status: 403,
    })
    expect(refreshTokenRequest).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('logs out and rejects when the refresh fails', async () => {
    const logout = vi.fn()
    client.setLogoutCallback(logout)
    refreshTokenRequest.mockResolvedValueOnce(false)
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { detail: 'Token expirado' }))

    await expect(client.get('/api/users/me')).rejects.toMatchObject({
      message: 'Session expired',
      status: 401,
    })
    expect(logout).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
