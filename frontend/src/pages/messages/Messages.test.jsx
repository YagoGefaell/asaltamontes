import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../../features/auth/hooks/useAuth', () => ({
  useAuth: () => ({
    currentUser: { username: 'ana' },
    sendMessage: vi.fn(),
    websocketConnected: true,
    websocketStatus: 'connected',
  }),
}))

const service = vi.hoisted(() => ({
  getGroupDetailsRequest: vi.fn(),
  deleteMessageRequest: vi.fn(),
}))
vi.mock('../../features/messages/services/messages.service', () => service)

import Messages from './Messages.jsx'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'

describe('Messages (BUG-052)', () => {
  it('clears the active chat when leaving /messages', () => {
    const setActiveChatId = vi.fn()
    const value = {
      chats: [],
      setChats: vi.fn(),
      chatMessages: {},
      setActiveChatId,
      upsertConversation: vi.fn(),
      ensurePrivateConversation: vi.fn(),
      createGroupConversation: vi.fn(),
      loadConversationMessages: vi.fn(),
      loadOlderMessages: vi.fn(),
      historyPaging: {},
      historyError: {},
      loadingChats: false,
      chatsPaging: { hasMore: false, loading: false },
      loadMoreChats: vi.fn(),
      errorChats: null,
      chatError: null,
      clearChatError: vi.fn(),
    }
    const { unmount } = render(
      <MemoryRouter>
        <ConversationsContext.Provider value={value}><Messages /></ConversationsContext.Provider>
      </MemoryRouter>,
    )
    expect(setActiveChatId).not.toHaveBeenCalled()

    unmount()

    expect(setActiveChatId).toHaveBeenCalledWith(null)
  })

  describe('moderation delete (T20d)', () => {
    const chat = { id: 'c1', type: 'GROUP', name: 'Comunidad', avatar: 'C', unreadCount: 0 }
    const messages = [{ id: 'm1', senderUsername: 'bea', content: 'hola', sentAt: '2026-10-22T10:00:00Z' }]

    function renderOpenChat(canModerateMessages) {
      window.HTMLElement.prototype.scrollIntoView = vi.fn()
      service.getGroupDetailsRequest.mockResolvedValue({ canModerateMessages })
      const markMessageDeleted = vi.fn()
      const value = {
        chats: [chat],
        setChats: vi.fn(),
        chatMessages: { c1: messages },
        setActiveChatId: vi.fn(),
        upsertConversation: vi.fn(),
        ensurePrivateConversation: vi.fn(),
        createGroupConversation: vi.fn(),
        loadConversationMessages: vi.fn().mockResolvedValue(undefined),
        loadOlderMessages: vi.fn(),
        historyPaging: { c1: { hasMore: false } },
        historyError: {},
        loadingChats: false,
        chatsPaging: { hasMore: false, loading: false },
        loadMoreChats: vi.fn(),
        errorChats: null,
        chatError: null,
        clearChatError: vi.fn(),
        markMessageDeleted,
      }
      render(
        <MemoryRouter initialEntries={[{ pathname: '/messages', state: { selectedConversation: { conversationId: 'c1', type: 'GROUP' } } }]}>
          <ConversationsContext.Provider value={value}><Messages /></ConversationsContext.Provider>
        </MemoryRouter>,
      )
      return markMessageDeleted
    }

    it('confirms with a modal and deletes the message', async () => {
      service.deleteMessageRequest.mockResolvedValue(undefined)
      const markMessageDeleted = renderOpenChat(true)

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar mensaje de bea' }))
      expect(service.deleteMessageRequest).not.toHaveBeenCalled()
      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar' }))

      await waitFor(() => expect(service.deleteMessageRequest).toHaveBeenCalledWith('c1', 'm1'))
      await waitFor(() => expect(markMessageDeleted).toHaveBeenCalledWith({ conversationId: 'c1', messageId: 'm1' }))
    })

    it('shows the server reason when the hierarchy forbids it', async () => {
      service.deleteMessageRequest.mockRejectedValue(new Error('Solo la propietaria puede borrar mensajes de la moderación'))
      const markMessageDeleted = renderOpenChat(true)

      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar mensaje de bea' }))
      fireEvent.click(await screen.findByRole('button', { name: 'Eliminar' }))

      expect(await screen.findByText('Solo la propietaria puede borrar mensajes de la moderación')).toBeInTheDocument()
      expect(markMessageDeleted).not.toHaveBeenCalled()
    })

    it('shows no delete action to who cannot moderate', async () => {
      renderOpenChat(false)

      await screen.findByText('hola')
      await waitFor(() => expect(service.getGroupDetailsRequest).toHaveBeenCalled())
      expect(screen.queryByRole('button', { name: /Eliminar mensaje/ })).not.toBeInTheDocument()
    })
  })
})
