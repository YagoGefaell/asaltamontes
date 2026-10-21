import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Fake stompjs Client: keeps the config so the test can play the server
const clients = []
vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(config) {
      Object.assign(this, config)
      this.config = config
      this.connected = false
      this.active = false
      this.activations = 0
      clients.push(this)
    }
    activate() {
      this.active = true
      this.activations += 1
      // Like stompjs: beforeConnect runs before the socket opens
      this.connecting = Promise.resolve(this.config.beforeConnect(this))
    }
    async deactivate() {
      this.active = false
      this.connected = false
    }
    // Test helpers
    serverAccepts() {
      this.connected = true
      this.config.onConnect({})
    }
    serverRejects(message) {
      this.config.onStompError({ headers: { message }, body: '' })
      this.socketCloses()
    }
    socketCloses() {
      this.connected = false
      this.config.onWebSocketClose({ code: 1006, reason: '' })
    }
  },
}))

const refreshSession = vi.fn()
const expireSession = vi.fn()
vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { refreshSession: (...args) => refreshSession(...args), expireSession: () => expireSession() },
}))

const { useWebSocket } = await import('./useWebSocket.js')
const { setAccessToken, clearAccessToken } = await import('../../auth/services/auth-token.js')

function jwt(secondsLeft) {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + secondsLeft }))
  return `h.${payload}.s`
}

const flush = () => act(async () => { await Promise.resolve(); await Promise.resolve() })

describe('useWebSocket reconnection (T11)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(Math, 'random').mockReturnValue(1)
    vi.spyOn(console, 'log').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    vi.spyOn(console, 'error').mockImplementation(() => {})
    clients.length = 0
    refreshSession.mockReset()
    expireSession.mockReset()
    setAccessToken(jwt(600))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    clearAccessToken()
  })

  it('connects with the current token while it is still valid', async () => {
    const token = jwt(600)
    setAccessToken(token)
    const { result } = renderHook(() => useWebSocket(true))
    await flush()

    const client = clients[0]
    expect(refreshSession).not.toHaveBeenCalled()
    expect(client.connectHeaders.Authorization).toBe(`Bearer ${token}`)

    act(() => client.serverAccepts())
    expect(result.current.websocketConnected).toBe(true)
    expect(result.current.websocketStatus).toBe('connected')
  })

  it('renews a token that is about to expire before connecting', async () => {
    setAccessToken(jwt(20))
    const renewed = jwt(900)
    refreshSession.mockImplementation(async () => { setAccessToken(renewed); return true })

    renderHook(() => useWebSocket(true))
    await flush()

    expect(refreshSession).toHaveBeenCalledTimes(1)
    expect(clients[0].connectHeaders.Authorization).toBe(`Bearer ${renewed}`)
  })

  it('reconnects with exponential backoff after the socket closes', async () => {
    const { result } = renderHook(() => useWebSocket(true))
    await flush()
    const client = clients[0]
    act(() => client.serverAccepts())

    act(() => client.socketCloses())
    expect(result.current.websocketStatus).toBe('reconnecting')

    await act(async () => { vi.advanceTimersByTime(999) })
    expect(client.activations).toBe(1)
    await act(async () => { vi.advanceTimersByTime(1) })
    await flush()
    expect(client.activations).toBe(2)

    // Second failure in a row: twice the wait
    act(() => client.socketCloses())
    await act(async () => { vi.advanceTimersByTime(1999) })
    expect(client.activations).toBe(2)
    await act(async () => { vi.advanceTimersByTime(1) })
    expect(client.activations).toBe(3)
  })

  it('forces a refresh when the server rejects the CONNECT, and logs out on the second rejection', async () => {
    refreshSession.mockResolvedValue(true)
    renderHook(() => useWebSocket(true))
    await flush()
    const client = clients[0]

    act(() => client.serverRejects('Token expirado'))
    await act(async () => { vi.advanceTimersByTime(1000) })
    await flush()
    expect(refreshSession).toHaveBeenCalledTimes(1)
    expect(expireSession).not.toHaveBeenCalled()

    act(() => client.serverRejects('Sesión cerrada o caducada'))
    expect(expireSession).toHaveBeenCalledTimes(1)
    await act(async () => { vi.advanceTimersByTime(60000) })
    // No more attempts once the session is over
    expect(client.activations).toBe(2)
  })

  it('does not refresh without network and reconnects as soon as it comes back', async () => {
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true)
    const { result } = renderHook(() => useWebSocket(true))
    await flush()
    const client = clients[0]
    act(() => client.serverAccepts())

    online.mockReturnValue(false)
    act(() => client.socketCloses())
    expect(result.current.websocketStatus).toBe('offline')
    await act(async () => { vi.advanceTimersByTime(60000) })
    expect(client.activations).toBe(1)

    online.mockReturnValue(true)
    await act(async () => { window.dispatchEvent(new Event('online')) })
    await flush()
    expect(client.activations).toBe(2)
    expect(refreshSession).not.toHaveBeenCalled()
  })

  it('stops reconnecting on logout', async () => {
    const { result, rerender } = renderHook(({ auth }) => useWebSocket(auth), { initialProps: { auth: true } })
    await flush()
    const client = clients[0]

    rerender({ auth: false })
    act(() => client.socketCloses())
    await act(async () => { vi.advanceTimersByTime(60000) })

    expect(client.activations).toBe(1)
    expect(result.current.websocketStatus).toBe('disconnected')
  })
})
