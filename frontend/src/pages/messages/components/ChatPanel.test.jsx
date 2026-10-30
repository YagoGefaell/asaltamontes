import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ChatPane from './ChatPanel.jsx'
import { groupSubtitle } from '../../../features/messages/utils/groupSubtitle'

function renderPanel(props = {}) {
  return render(
    <ChatPane
      selectedChat={{ id: 1, name: 'Bea', type: 'INDIVIDUAL' }}
      currentMessages={[]}
      currentUsername="ana"
      draftMessage=""
      onDraftChange={vi.fn()}
      onSendMessage={vi.fn()}
      onLeaveChat={vi.fn()}
      onOpenUserProfile={vi.fn()}
      onSelectedAvatarError={vi.fn()}
      websocketConnected
      messagesEndRef={{ current: null }}
      {...props}
    />,
  )
}

describe('ChatPanel', () => {
  it('limits the input to the backend maximum of 2000 characters', () => {
    renderPanel()

    expect(screen.getByLabelText('Mensaje')).toHaveAttribute('maxLength', '2000')
  })

  it('shows the character counter only near the limit', () => {
    const { rerender } = renderPanel({ draftMessage: 'hola' })
    expect(screen.queryByText(/\/2000$/)).not.toBeInTheDocument()

    rerender(
      <ChatPane
        selectedChat={{ id: 1, name: 'Bea', type: 'INDIVIDUAL' }}
        currentMessages={[]}
        currentUsername="ana"
        draftMessage={'x'.repeat(1850)}
        onDraftChange={vi.fn()}
        onSendMessage={vi.fn()}
        onLeaveChat={vi.fn()}
        onOpenUserProfile={vi.fn()}
        onSelectedAvatarError={vi.fn()}
        websocketConnected
        messagesEndRef={{ current: null }}
      />,
    )
    expect(screen.getByText('1850/2000')).toBeInTheDocument()
  })

  it('shows the reason when the server rejects a message', () => {
    renderPanel({ sendError: 'No tienes permiso para escribir en este chat' })

    expect(screen.getByRole('alert')).toHaveTextContent('No tienes permiso para escribir en este chat')
  })

  it('shows a retry instead of the empty-chat text when the history failed (BUG-061)', () => {
    const onRetryHistory = vi.fn()
    renderPanel({ historyStatus: 'error', onRetryHistory })

    expect(screen.queryByText(/Empieza la conversación/)).not.toBeInTheDocument()
    expect(screen.getByText(/No se pudo cargar la conversación/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetryHistory).toHaveBeenCalledTimes(1)
  })

  it('does not show the empty-chat text while the history is loading (BUG-061)', () => {
    renderPanel({ historyStatus: 'loading' })

    expect(screen.queryByText(/Empieza la conversación/)).not.toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Cargando mensajes' })).toBeInTheDocument()
  })

  it('shows the empty-chat text only when the history loaded with no messages', () => {
    renderPanel({ historyStatus: 'loaded' })

    expect(screen.getByText('Empieza la conversación con @Bea')).toBeInTheDocument()
  })

  it('announces new messages politely in a labelled log region', () => {
    renderPanel()

    const log = screen.getByRole('log', { name: 'Mensajes con Bea' })
    expect(log).toHaveAttribute('aria-live', 'polite')
  })

  it('inserts a day separator when the day changes', () => {
    const messages = [
      { id: 1, senderUsername: 'bea', content: 'hola', sentAt: '2020-01-01T10:00:00Z' },
      { id: 2, senderUsername: 'ana', content: 'que tal', sentAt: '2020-01-01T10:05:00Z' },
      { id: 3, senderUsername: 'bea', content: 'bien', sentAt: '2020-01-05T10:00:00Z' },
    ]
    renderPanel({ currentMessages: messages })

    expect(screen.getAllByRole('separator')).toHaveLength(2)
  })

  it('disables the send button without text or connection and is a round icon button', () => {
    const { rerender } = renderPanel({ draftMessage: '' })
    expect(screen.getByRole('button', { name: 'Enviar mensaje' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Enviar mensaje' })).toHaveClass('message-send-btn')

    rerender(
      <ChatPane
        selectedChat={{ id: 1, name: 'Bea', type: 'INDIVIDUAL' }}
        currentMessages={[]}
        currentUsername="ana"
        draftMessage="hola"
        onDraftChange={vi.fn()}
        onSendMessage={vi.fn()}
        onLeaveChat={vi.fn()}
        onOpenUserProfile={vi.fn()}
        onSelectedAvatarError={vi.fn()}
        websocketConnected
        messagesEndRef={{ current: null }}
      />,
    )
    expect(screen.getByRole('button', { name: 'Enviar mensaje' })).toBeEnabled()
  })

  it("formats message times with the viewer's locale and time zone, not a fixed one (BUG-063, D-019)", () => {
    const spy = vi.spyOn(Intl, 'DateTimeFormat')
    renderPanel({ currentMessages: [{ id: 1, content: 'hola', senderUsername: 'bea', sentAt: '2026-10-23T13:30:00Z' }] })

    expect(spy).toHaveBeenCalled()
    for (const [locale, options] of spy.mock.calls) {
      expect(locale).toBeUndefined()
      expect(options).not.toHaveProperty('timeZone')
    }
    spy.mockRestore()
  })

  it('renders system messages of a group centered, composed from the payload (T12)', () => {
    renderPanel({
      selectedChat: { id: 1, name: 'Trail', type: 'GROUP' },
      currentMessages: [{
        id: 'm1', type: 'SYSTEM', content: 'respaldo', senderUsername: null, sentAt: '2026-10-27T10:00:00',
        systemPayload: { action: 'MEMBER_ADDED', actor: 'bea', targets: ['ana'] },
      }],
    })

    expect(screen.getByText('@bea te añadió al grupo')).toHaveClass('message-system')
    expect(screen.queryByText('respaldo')).not.toBeInTheDocument()
  })

  it('opens the group info from the header name, with the participants below and no fake status or info icon (T29q)', () => {
    const onOpenGroupInfo = vi.fn()
    renderPanel({
      selectedChat: { id: 1, name: 'Trail', type: 'GROUP' },
      onOpenGroupInfo,
      groupParticipants: { count: 7, names: ['Ana', 'Lucía', 'Marta'] },
    })

    expect(screen.getByText('Ana, Lucía, Marta y 4 más')).toBeInTheDocument()
    expect(screen.queryByText('Toca para ver el grupo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Info del grupo' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('Trail'))

    expect(onOpenGroupInfo).toHaveBeenCalled()
  })

  it('shows @username instead of a fake "En linea" in private chats (T29q)', () => {
    renderPanel()

    expect(screen.getByText('@Bea')).toBeInTheDocument()
    expect(screen.queryByText('En linea')).not.toBeInTheDocument()
  })

  it('builds the group subtitle (T29q)', () => {
    expect(groupSubtitle(['Ana', 'Lucía', 'Marta'], 7)).toBe('Ana, Lucía, Marta y 4 más')
    expect(groupSubtitle(['Ana', 'Lucía'], 2)).toBe('Ana y Lucía')
    expect(groupSubtitle([], 7)).toBe('7 participantes')
    expect(groupSubtitle(null, 0)).toBe('')
  })

  it('sends with Enter on desktop but not with Shift+Enter (T29q)', () => {
    const onSendMessage = vi.fn()
    window.matchMedia = vi.fn().mockReturnValue({ matches: true })
    renderPanel({ draftMessage: 'hola', onSendMessage })
    const input = screen.getByLabelText('Mensaje')

    fireEvent.keyDown(input, { key: 'Enter', shiftKey: true })
    expect(onSendMessage).not.toHaveBeenCalled()
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onSendMessage).toHaveBeenCalledTimes(1)
    delete window.matchMedia
  })

  describe('author blocks in groups (T29q)', () => {
    const group = { id: 1, name: 'Trail', type: 'GROUP' }
    const msg = (id, user, time, extra = {}) => ({
      id, senderUsername: user, senderFullName: `${user} Pérez`, senderAvatarUrl: null,
      content: `m${id}`, sentAt: `2026-10-22T${time}`, ...extra,
    })

    it('shows one avatar per block, no name or @, and opens the profile', () => {
      const onOpenAuthorProfile = vi.fn()
      renderPanel({
        selectedChat: group,
        onOpenAuthorProfile,
        currentMessages: [
          msg(1, 'bea', '10:00:00Z'),
          msg(2, 'bea', '10:02:00Z'),
          msg(3, 'bea', '10:08:00Z'), // 6 minutes later: new block
          msg(4, 'cris', '10:09:00Z'),
        ],
      })

      expect(screen.getAllByRole('button', { name: /Ver perfil de/ })).toHaveLength(3)
      expect(screen.queryByText('@bea')).not.toBeInTheDocument()
      expect(screen.queryByText('Propietaria')).not.toBeInTheDocument()
      expect(screen.getAllByRole('group', { name: 'bea Pérez' })).toHaveLength(2)
      fireEvent.click(screen.getAllByRole('button', { name: 'Ver perfil de cris Pérez' })[0])
      expect(onOpenAuthorProfile).toHaveBeenCalledWith('cris')
    })

    it('breaks the block at a system message and does not show avatars on my messages', () => {
      renderPanel({
        selectedChat: group,
        currentMessages: [
          msg(1, 'bea', '10:00:00Z'),
          { id: 2, type: 'SYSTEM', senderUsername: null, content: 'x', sentAt: '2026-10-22T10:01:00Z', systemPayload: { action: 'MEMBER_ADDED', actor: 'bea', targets: ['cris'] } },
          msg(3, 'bea', '10:02:00Z'),
          msg(4, 'ana', '10:03:00Z'),
        ],
      })

      expect(screen.getAllByRole('button', { name: 'Ver perfil de bea Pérez' })).toHaveLength(2)
      expect(screen.queryByRole('button', { name: 'Ver perfil de ana Pérez' })).not.toBeInTheDocument()
    })

    it('shows no avatars in private chats', () => {
      renderPanel({ currentMessages: [msg(1, 'bea', '10:00:00Z')] })

      expect(screen.queryByRole('button', { name: /Ver perfil de/ })).not.toBeInTheDocument()
    })
  })

  describe('moderation delete (T20d)', () => {
    const messages = [
      { id: 'm1', senderUsername: 'bea', content: 'hola', sentAt: '2026-10-22T10:00:00Z' },
      { id: 'm2', senderUsername: 'cris', content: '', deleted: true, sentAt: '2026-10-22T10:01:00Z' },
    ]
    const group = { id: 1, name: 'Comunidad', type: 'GROUP' }

    it('offers the delete action only to moderation, and reports the message', () => {
      const onDeleteMessage = vi.fn()
      const { unmount } = renderPanel({ selectedChat: group, currentMessages: messages })
      expect(screen.queryByRole('button', { name: /Eliminar mensaje/ })).not.toBeInTheDocument()
      unmount()

      renderPanel({ selectedChat: group, currentMessages: messages, moderationRole: 'OWNER', onDeleteMessage })
      fireEvent.click(screen.getByRole('button', { name: 'Eliminar mensaje de bea' }))

      expect(onDeleteMessage).toHaveBeenCalledWith(messages[0])
      // the already deleted one has no action
      expect(screen.getAllByRole('button', { name: /Eliminar mensaje/ })).toHaveLength(1)
    })

    it('shows no role labels and hides the delete action where the server would refuse (T20e)', () => {
      const staff = [
        { id: 'm1', senderUsername: 'bea', content: 'hola', authorRole: 'OWNER', sentAt: '2026-10-22T10:00:00Z' },
        { id: 'm2', senderUsername: 'cris', content: 'ey', authorRole: 'MODERATOR', sentAt: '2026-10-22T10:01:00Z' },
        { id: 'm3', senderUsername: 'dani', content: 'buenas', sentAt: '2026-10-22T10:02:00Z' },
      ]
      renderPanel({ selectedChat: group, currentMessages: staff, moderationRole: 'MODERATOR', onDeleteMessage: vi.fn() })

      expect(screen.queryByText('Propietaria')).not.toBeInTheDocument()
      expect(screen.queryByText('Moderadora')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Eliminar mensaje de bea' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Eliminar mensaje de cris' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Eliminar mensaje de dani' })).toBeInTheDocument()
    })

    it('shows a deleted message as a moderation notice, for everybody', () => {
      renderPanel({ selectedChat: group, currentMessages: messages })

      expect(screen.getByText('Mensaje eliminado por moderación')).toBeInTheDocument()
    })
  })

  describe('older history (BUG-053)', () => {
    const observers = []
    beforeEach(() => {
      observers.length = 0
      globalThis.IntersectionObserver = class {
        constructor(cb) { this.cb = cb; observers.push(this) }
        observe() { this.cb([{ isIntersecting: true }]) }
        disconnect() {}
      }
    })
    afterEach(() => { delete globalThis.IntersectionObserver })

    it('does not auto-load again after a failure and offers a manual retry', () => {
      const onLoadOlder = vi.fn()
      renderPanel({ hasMoreHistory: true, olderError: true, onLoadOlder })

      expect(onLoadOlder).not.toHaveBeenCalled()
      expect(screen.getByRole('alert')).toHaveTextContent('No se pudieron cargar los mensajes anteriores')
      fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
      expect(onLoadOlder).toHaveBeenCalledTimes(1)
    })

    it('does not recreate the observer when only the callback identity changes', () => {
      const { rerender } = renderPanel({ hasMoreHistory: true, onLoadOlder: vi.fn() })
      const created = observers.length
      rerender(
        <ChatPane
          selectedChat={{ id: 1, name: 'Bea', type: 'INDIVIDUAL' }}
          currentMessages={[]}
          currentUsername="ana"
          draftMessage="h"
          onDraftChange={vi.fn()}
          onSendMessage={vi.fn()}
          onLeaveChat={vi.fn()}
          onOpenUserProfile={vi.fn()}
          onSelectedAvatarError={vi.fn()}
          websocketConnected
          messagesEndRef={{ current: null }}
          hasMoreHistory
          onLoadOlder={vi.fn()}
        />,
      )
      expect(observers.length).toBe(created)
    })
  })
})
