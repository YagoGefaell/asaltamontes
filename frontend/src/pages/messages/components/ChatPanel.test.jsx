import { render, screen } from '@testing-library/react'
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
})
