import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useGlobalMessageListener } from './useGlobalMessageListener.js'

function fakeStompClient() {
  const handlers = {}
  const unsubscribes = []
  return {
    handlers,
    unsubscribes,
    subscribe: vi.fn((destination, handler) => {
      handlers[destination] = handler
      const unsubscribe = vi.fn()
      unsubscribes.push(unsubscribe)
      return { unsubscribe }
    }),
  }
}

describe('useGlobalMessageListener', () => {
  it('delivers incoming messages and chat errors to their callbacks', () => {
    const client = fakeStompClient()
    const onMessage = vi.fn()
    const onError = vi.fn()

    renderHook(() => useGlobalMessageListener(client, true, onMessage, onError))

    client.handlers['/user/queue/messages']({ body: JSON.stringify({ id: 1, content: 'hola' }) })
    client.handlers['/user/queue/errors']({ body: JSON.stringify({ detail: 'No tienes permiso' }) })

    expect(onMessage).toHaveBeenCalledWith({ id: 1, content: 'hola' })
    expect(onError).toHaveBeenCalledWith({ detail: 'No tienes permiso' })
  })

  it('does not subscribe until the socket is connected and unsubscribes on unmount', () => {
    const client = fakeStompClient()

    const { rerender, unmount } = renderHook(
      ({ connected }) => useGlobalMessageListener(client, connected, vi.fn(), vi.fn()),
      { initialProps: { connected: false } },
    )
    expect(client.subscribe).not.toHaveBeenCalled()

    rerender({ connected: true })
    expect(client.subscribe).toHaveBeenCalledTimes(2)

    unmount()
    client.unsubscribes.forEach((unsubscribe) => expect(unsubscribe).toHaveBeenCalled())
  })
})
