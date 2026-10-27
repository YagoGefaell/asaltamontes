import { useContext, useEffect } from 'react'
import { act, render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted(() => ({ client: null, connected: false, authenticated: true }))

vi.mock('../../auth/hooks/useAuth', () => ({
  useAuth: () => ({
    websocketClient: auth.client,
    websocketConnected: auth.connected,
    isAuthenticated: auth.authenticated,
  }),
}))
vi.mock('../services/notifications.service.js', () => ({
  getNotificationsRequest: vi.fn(),
  getUnreadCountRequest: vi.fn(),
  markNotificationsReadRequest: vi.fn(),
  markAllNotificationsReadRequest: vi.fn(),
}))

import { NotificationsProvider } from './notifications.provider.jsx'
import { mergeNotifications } from '../utils/mergeNotifications.js'
import { NotificationsContext } from '../hooks/notifications.context'
import {
  getNotificationsRequest,
  getUnreadCountRequest,
  markAllNotificationsReadRequest,
  markNotificationsReadRequest,
} from '../services/notifications.service.js'

const item = (id, readAt = null) => ({ id, type: 'NEW_FOLLOWER', readAt, createdAt: '2026-10-27T10:00:00Z' })

function fakeClient() {
  const handlers = {}
  return {
    handlers,
    subscribe: vi.fn((destination, handler) => {
      handlers[destination] = handler
      return { unsubscribe: vi.fn() }
    }),
  }
}

function renderProvider() {
  const ctx = { current: null }
  const Probe = () => {
    const value = useContext(NotificationsContext)
    useEffect(() => {
      ctx.current = value
    })
    return null
  }
  const utils = render(<NotificationsProvider><Probe /></NotificationsProvider>)
  ctx.rerender = () => utils.rerender(<NotificationsProvider><Probe /></NotificationsProvider>)
  return ctx
}

describe('NotificationsProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.client = null
    auth.connected = false
    auth.authenticated = true
    getUnreadCountRequest.mockResolvedValue(2)
    getNotificationsRequest.mockResolvedValue({ items: [item('a'), item('b')], hasMore: true, nextBefore: 'tok' })
    markNotificationsReadRequest.mockResolvedValue(null)
    markAllNotificationsReadRequest.mockResolvedValue(null)
  })

  it('loads the counter and the first page on start', async () => {
    const ctx = renderProvider()

    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))
    expect(ctx.current.items.map((n) => n.id)).toEqual(['a', 'b'])
    expect(ctx.current.hasMore).toBe(true)
  })

  it('does not load anything when logged out', async () => {
    auth.authenticated = false
    renderProvider()
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(getUnreadCountRequest).not.toHaveBeenCalled()
  })

  it('a notification that arrives over WebSocket goes first and raises the counter', async () => {
    auth.client = fakeClient()
    auth.connected = true
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))

    act(() => auth.client.handlers['/user/queue/notifications']({ body: JSON.stringify(item('new')) }))

    expect(ctx.current.items[0].id).toBe('new')
    expect(ctx.current.unreadCount).toBe(3)

    // The same frame twice does not count twice
    act(() => auth.client.handlers['/user/queue/notifications']({ body: JSON.stringify(item('new')) }))
    expect(ctx.current.unreadCount).toBe(3)
  })

  it('asks again for the counter and the first page when the socket (re)connects', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))
    getUnreadCountRequest.mockClear()
    getUnreadCountRequest.mockResolvedValue(5)

    auth.client = fakeClient()
    auth.connected = true
    ctx.rerender()

    await waitFor(() => expect(ctx.current.unreadCount).toBe(5))
    expect(getUnreadCountRequest).toHaveBeenCalled()
    expect(auth.client.subscribe).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function))
  })

  it('marks one as read optimistically, and the counter never goes below zero', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))

    await act(() => ctx.current.markRead(ctx.current.items[0]))

    expect(markNotificationsReadRequest).toHaveBeenCalledWith(['a'])
    expect(ctx.current.unreadCount).toBe(1)
    expect(ctx.current.items[0].readAt).not.toBeNull()
    // Already read: nothing more happens
    await act(() => ctx.current.markRead(ctx.current.items[0]))
    expect(markNotificationsReadRequest).toHaveBeenCalledTimes(1)
  })

  it('marks everything as read', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))

    await act(() => ctx.current.markAllRead())

    expect(ctx.current.unreadCount).toBe(0)
    expect(ctx.current.items.every((n) => n.readAt)).toBe(true)
    expect(markAllNotificationsReadRequest).toHaveBeenCalled()
  })

  it('goes back to the server truth when marking fails', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))
    markNotificationsReadRequest.mockRejectedValue(new Error('x'))

    await act(() => ctx.current.markRead(ctx.current.items[0]))

    await waitFor(() => expect(ctx.current.unreadCount).toBe(2))
    expect(ctx.current.items[0].readAt).toBeNull()
  })

  it('loads the next page with the cursor and keeps ids unique', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.hasMore).toBe(true))
    getNotificationsRequest.mockResolvedValue({ items: [item('b'), item('c')], hasMore: false, nextBefore: null })

    await act(() => ctx.current.loadMore())

    expect(getNotificationsRequest).toHaveBeenLastCalledWith({ before: 'tok' })
    expect(ctx.current.items.map((n) => n.id)).toEqual(['a', 'b', 'c'])
    expect(ctx.current.hasMore).toBe(false)
  })
})

describe('mergeNotifications', () => {
  it('appends only what is new', () => {
    expect(mergeNotifications([{ id: 1 }], [{ id: 1 }, { id: 2 }])).toEqual([{ id: 1 }, { id: 2 }])
  })
})
