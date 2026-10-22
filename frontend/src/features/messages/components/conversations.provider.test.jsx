import { useContext, useEffect } from 'react'
import { act, render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const listener = vi.hoisted(() => ({ onMessage: null }))

vi.mock('../../auth/hooks/useAuth', () => ({
  useAuth: () => ({
    websocketClient: null,
    websocketConnected: false,
    isAuthenticated: true,
    sendMessage: vi.fn(),
    currentUser: { username: 'ana' },
  }),
}))
vi.mock('../hooks/useGlobalMessageListener', () => ({
  useGlobalMessageListener: (_client, _connected, onMessage) => {
    listener.onMessage = onMessage
  },
}))
vi.mock('../services/messages.service.js', () => ({
  getMyConversationsRequest: vi.fn().mockResolvedValue({ items: [], hasMore: false }),
  getConversationMessagesRequest: vi.fn(),
  getMessagesAfterRequest: vi.fn().mockResolvedValue([]),
  markConversationReadRequest: vi.fn().mockResolvedValue(undefined),
  getPrivateConversationRequest: vi.fn(),
  createPrivateConversationRequest: vi.fn(),
  createGroupConversationRequest: vi.fn(),
}))

import { ConversationsProvider } from './conversations.provider.jsx'
import { ConversationsContext } from '../hooks/conversations.context'
import { getConversationMessagesRequest, markConversationReadRequest } from '../services/messages.service.js'

function renderProvider() {
  const ctx = { current: null }
  const Probe = () => {
    const value = useContext(ConversationsContext)
    useEffect(() => {
      ctx.current = value
    })
    return null
  }
  render(<ConversationsProvider><Probe /></ConversationsProvider>)
  return ctx
}

describe('ConversationsProvider history loading (BUG-051)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listener.onMessage = null
  })

  it('still loads the history of a chat that only received a live message', async () => {
    getConversationMessagesRequest.mockResolvedValue({
      items: [{ id: 1, content: 'antiguo', senderUsername: 'bea', sentAt: '2026-10-22T10:00:00Z' }],
      hasMore: true,
      nextBefore: 1,
    })
    const ctx = renderProvider()
    await waitFor(() => expect(listener.onMessage).toBeTypeOf('function'))

    act(() => {
      listener.onMessage({ id: 2, conversationId: 7, content: 'nuevo', senderUsername: 'bea', sentAt: '2026-10-22T10:05:00Z' })
    })
    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })

    expect(getConversationMessagesRequest).toHaveBeenCalledWith(7)
    expect(ctx.current.chatMessages[7].map((m) => m.id)).toEqual([1, 2])
    expect(ctx.current.historyPaging[7].hasMore).toBe(true)
  })

  it('does not reload a chat whose first page is already loaded', async () => {
    getConversationMessagesRequest.mockResolvedValue({ items: [{ id: 1, content: 'a', senderUsername: 'bea', sentAt: '2026-10-22T10:00:00Z' }], hasMore: false, nextBefore: null })
    const ctx = renderProvider()
    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })
    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })

    expect(getConversationMessagesRequest).toHaveBeenCalledTimes(1)
  })
})

describe('ConversationsProvider history failure (BUG-061)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listener.onMessage = null
  })

  it('flags the error and does not mark the chat as read when the history fails, then recovers on retry', async () => {
    getConversationMessagesRequest.mockRejectedValueOnce(new Error('boom'))
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current).not.toBeNull())
    act(() => {
      ctx.current.setChats([{ id: 7, type: 'INDIVIDUAL', name: 'Bea', unreadCount: 3 }])
      ctx.current.setActiveChatId(7)
    })

    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })

    expect(ctx.current.historyError[7]).toBe(true)
    expect(ctx.current.historyPaging[7]).toBeUndefined()
    expect(ctx.current.chats.find((c) => c.id === 7).unreadCount).toBe(3)
    expect(markConversationReadRequest).not.toHaveBeenCalled()

    getConversationMessagesRequest.mockResolvedValueOnce({
      items: [{ id: 1, content: 'hola', senderUsername: 'bea', sentAt: '2026-10-22T10:00:00Z' }],
      hasMore: false,
      nextBefore: null,
    })
    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })

    expect(ctx.current.historyError[7]).toBe(false)
    expect(ctx.current.historyPaging[7]).toBeDefined()
    expect(ctx.current.chats.find((c) => c.id === 7).unreadCount).toBe(0)
  })
})
