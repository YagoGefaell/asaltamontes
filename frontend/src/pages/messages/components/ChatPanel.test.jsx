import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ChatPane from './ChatPanel.jsx'

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

  it('disables the send button without text or connection and uses the shared Button', () => {
    const { rerender } = renderPanel({ draftMessage: '' })
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Enviar' })).toHaveClass('btn')

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
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeEnabled()
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
})
