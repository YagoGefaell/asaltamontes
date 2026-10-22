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

    expect(screen.queryByText(/Empieza la conversacion/)).not.toBeInTheDocument()
    expect(screen.getByText(/No se pudo cargar la conversación/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetryHistory).toHaveBeenCalledTimes(1)
  })

  it('does not show the empty-chat text while the history is loading (BUG-061)', () => {
    renderPanel({ historyStatus: 'loading' })

    expect(screen.queryByText(/Empieza la conversacion/)).not.toBeInTheDocument()
    expect(screen.getByText('Cargando mensajes…')).toBeInTheDocument()
  })

  it('shows the empty-chat text only when the history loaded with no messages', () => {
    renderPanel({ historyStatus: 'loaded' })

    expect(screen.getByText('Empieza la conversacion con @Bea')).toBeInTheDocument()
  })
})
