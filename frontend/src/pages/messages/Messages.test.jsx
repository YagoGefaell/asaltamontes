import { render } from '@testing-library/react'
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

import { ToastProvider } from '../../shared/ui/Toast.jsx'
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
        <ToastProvider><ConversationsContext.Provider value={value}><Messages /></ConversationsContext.Provider></ToastProvider>
      </MemoryRouter>,
    )
    expect(setActiveChatId).not.toHaveBeenCalled()

    unmount()

    expect(setActiveChatId).toHaveBeenCalledWith(null)
  })
})
