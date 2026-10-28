import { useContext, useEffect } from 'react'
import { act, render, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const listener = vi.hoisted(() => ({ onMessage: null, onRemoved: null, onDeleted: null }))
const auth = vi.hoisted(() => ({ isAuthenticated: true, sendMessage: () => {} }))

vi.mock('../../auth/hooks/useAuth', () => ({
  useAuth: () => ({
    websocketClient: null,
    websocketConnected: false,
    isAuthenticated: auth.isAuthenticated,
    sendMessage: auth.sendMessage,
    currentUser: { username: 'ana' },
  }),
}))
vi.mock('../hooks/useGlobalMessageListener', () => ({
  useGlobalMessageListener: (_client, _connected, onMessage, _onError, onRemoved, onDeleted) => {
    listener.onMessage = onMessage
    listener.onRemoved = onRemoved
    listener.onDeleted = onDeleted
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
import { getConversationMessagesRequest, getMyConversationsRequest, markConversationReadRequest } from '../services/messages.service.js'

function renderProvider() {
  auth.isAuthenticated = true
  auth.sendMessage = vi.fn()
  const ctx = { current: null }
  const Probe = () => {
    const value = useContext(ConversationsContext)
    useEffect(() => {
      ctx.current = value
    })
    return null
  }
  const tree = <ConversationsProvider><Probe /></ConversationsProvider>
  const utils = render(tree)
  ctx.rerender = () => utils.rerender(<ConversationsProvider><Probe /></ConversationsProvider>)
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
      nextBefore: 'tok_1',
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

describe('ConversationsProvider active chat (BUG-052)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listener.onMessage = null
  })

  const live = { id: 2, conversationId: 7, content: 'nuevo', senderUsername: 'bea', sentAt: '2026-10-22T10:05:00Z' }

  it('counts a live message as unread once the active chat is cleared', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(listener.onMessage).toBeTypeOf('function'))
    act(() => {
      ctx.current.setChats([{ id: 7, type: 'INDIVIDUAL', name: 'Bea', unreadCount: 0 }])
      ctx.current.setActiveChatId(7)
    })
    act(() => listener.onMessage(live))
    expect(ctx.current.chats[0].unreadCount).toBe(0)
    expect(markConversationReadRequest).toHaveBeenCalledWith(7)

    markConversationReadRequest.mockClear()
    act(() => ctx.current.setActiveChatId(null))
    act(() => listener.onMessage({ ...live, id: 3 }))

    expect(ctx.current.chats[0].unreadCount).toBe(1)
    expect(markConversationReadRequest).not.toHaveBeenCalled()
  })

  it('clears the active chat on logout', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current).not.toBeNull())
    act(() => ctx.current.setActiveChatId(7))
    expect(ctx.current.activeChatId).toBe(7)

    auth.isAuthenticated = false
    act(() => ctx.rerender())

    expect(ctx.current.activeChatId).toBeNull()
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

describe('ConversationsProvider chat list cursor (T30c)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('asks for the next page of chats with the opaque nextBefore token it was given', async () => {
    const chat = (id) => ({ conversationId: id, displayName: 'x', type: 'INDIVIDUAL', unreadCount: 0, lastMessageAt: null })
    getMyConversationsRequest
      .mockResolvedValueOnce({ items: [chat('a')], hasMore: true, nextBefore: 'tok_page2', unreadChats: 0 })
      .mockResolvedValueOnce({ items: [chat('b')], hasMore: false, nextBefore: null, unreadChats: 0 })
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current?.chatsPaging?.nextBefore).toBe('tok_page2'))

    await act(async () => {
      await ctx.current.loadMoreChats()
    })

    expect(getMyConversationsRequest).toHaveBeenLastCalledWith({ before: 'tok_page2' })
  })
})

describe('ConversationsProvider group events (T12)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listener.onMessage = null
  })

  it('drops a chat the user was removed from and reports it', async () => {
    getMyConversationsRequest.mockResolvedValue({
      items: [{ conversationId: 'g1', displayName: 'Trail', type: 'GROUP', unreadCount: 0 }],
      hasMore: false,
    })
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.chats).toHaveLength(1))

    act(() => listener.onRemoved({ conversationId: 'g1', reason: 'REMOVED' }))

    await waitFor(() => expect(ctx.current.chats).toHaveLength(0))
    expect(ctx.current.removedChat).toMatchObject({ id: 'g1', reason: 'REMOVED' })
  })

  it('updates the name of a renamed group and does not count my own action as unread', async () => {
    getMyConversationsRequest.mockResolvedValue({
      items: [{ conversationId: 'g1', displayName: 'Trail', type: 'GROUP', unreadCount: 0 }],
      hasMore: false,
    })
    const ctx = renderProvider()
    await waitFor(() => expect(ctx.current.chats).toHaveLength(1))

    act(() => listener.onMessage({
      id: 'm1', conversationId: 'g1', type: 'SYSTEM', content: 'ana cambió el nombre', senderUsername: null,
      sentAt: '2026-10-27T10:00:00', systemPayload: { action: 'GROUP_RENAMED', actor: 'ana', name: 'Montaña' },
    }))

    await waitFor(() => expect(ctx.current.chats[0].name).toBe('Montaña'))
    expect(ctx.current.chats[0].unreadCount).toBe(0)
    expect(ctx.current.chatMessages.g1[0].type).toBe('SYSTEM')
  })
})

describe('ConversationsProvider moderation deletes (T20d)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listener.onMessage = null
    listener.onDeleted = null
  })

  it('turns a deleted message into a notice and asks the server for the new preview', async () => {
    getConversationMessagesRequest.mockResolvedValue({
      items: [
        { id: 1, content: 'hola', senderUsername: 'bea', sentAt: '2026-10-22T10:00:00Z' },
        { id: 2, content: 'insulto', senderUsername: 'cris', sentAt: '2026-10-22T10:01:00Z' },
      ],
      hasMore: false,
      nextBefore: null,
    })
    const ctx = renderProvider()
    await waitFor(() => expect(listener.onDeleted).toBeTypeOf('function'))
    await act(async () => {
      await ctx.current.loadConversationMessages(7)
    })
    getMyConversationsRequest.mockClear()

    act(() => listener.onDeleted({ conversationId: 7, messageId: 2 }))

    const [kept, deleted] = ctx.current.chatMessages[7]
    expect(kept).toMatchObject({ id: 1, content: 'hola' })
    expect(deleted).toMatchObject({ id: 2, deleted: true, content: '' })
    await waitFor(() => expect(getMyConversationsRequest).toHaveBeenCalled())
  })

  it('ignores deletions of chats or messages that are not loaded', async () => {
    const ctx = renderProvider()
    await waitFor(() => expect(listener.onDeleted).toBeTypeOf('function'))
    getMyConversationsRequest.mockClear()

    act(() => listener.onDeleted({ conversationId: 9, messageId: 1 }))

    expect(ctx.current.chatMessages).toEqual({})
  })
})
