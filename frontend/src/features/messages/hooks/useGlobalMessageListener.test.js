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

  it('tells when a chat has to leave the list (T12)', () => {
    const client = fakeStompClient()
    const onRemoved = vi.fn()

    renderHook(() => useGlobalMessageListener(client, true, vi.fn(), vi.fn(), onRemoved))
    client.handlers['/user/queue/conversations']({ body: JSON.stringify({ conversationId: 'c1', reason: 'REMOVED' }) })

    expect(onRemoved).toHaveBeenCalledWith({ conversationId: 'c1', reason: 'REMOVED' })
  })

  it('tells when a moderator deleted a message (T20d)', () => {
    const client = fakeStompClient()
    const onDeleted = vi.fn()

    renderHook(() => useGlobalMessageListener(client, true, vi.fn(), vi.fn(), null, onDeleted))
    client.handlers['/user/queue/message-deletions']({ body: JSON.stringify({ conversationId: 'c1', messageId: 'm1' }) })

    expect(onDeleted).toHaveBeenCalledWith({ conversationId: 'c1', messageId: 'm1' })
  })

  it('does not subscribe until the socket is connected and unsubscribes on unmount', () => {
    const client = fakeStompClient()

    const { rerender, unmount } = renderHook(
      ({ connected }) => useGlobalMessageListener(client, connected, vi.fn(), vi.fn()),
      { initialProps: { connected: false } },
    )
    expect(client.subscribe).not.toHaveBeenCalled()

    rerender({ connected: true })
    expect(client.subscribe).toHaveBeenCalledTimes(4)

    unmount()
    client.unsubscribes.forEach((unsubscribe) => expect(unsubscribe).toHaveBeenCalled())
  })

  it('does not throw when the connection is already gone on cleanup (T11)', () => {
    const client = fakeStompClient()
    client.subscribe.mockImplementation(() => ({
      // What stompjs does when the socket has closed
      unsubscribe: () => { throw new TypeError('There is no underlying STOMP connection') },
    }))

    const { rerender } = renderHook(
      ({ connected }) => useGlobalMessageListener(client, connected, vi.fn(), vi.fn()),
      { initialProps: { connected: true } },
    )

    expect(() => rerender({ connected: false })).not.toThrow()
  })
})
