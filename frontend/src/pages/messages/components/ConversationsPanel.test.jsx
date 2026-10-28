import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ConversationsPane from './ConversationsPanel.jsx'

function renderPane(props = {}) {
  return render(
    <ConversationsPane
      loadingChats={false}
      errorChats={null}
      chats={[]}
      onEnterChat={vi.fn()}
      onAvatarError={vi.fn()}
      formatMessageTime={() => '10:00'}
      onCreateGroup={vi.fn()}
      creatingGroup={false}
      {...props}
    />,
  )
}

describe('ConversationsPanel', () => {
  it('shows an empty state with guidance when there are no conversations', () => {
    renderPane()

    expect(screen.getByText('Aún no tienes conversaciones.')).toBeInTheDocument()
    expect(screen.getByText(/crea un grupo/)).toBeInTheDocument()
  })

  it('shows the loading skeleton as a labelled status', () => {
    renderPane({ loadingChats: true })

    expect(screen.getByRole('status', { name: 'Cargando conversaciones' })).toBeInTheDocument()
  })

  it('shows the error state instead of the empty one', () => {
    renderPane({ errorChats: 'Fallo de red' })

    expect(screen.getByText('No se pudieron cargar las conversaciones.')).toBeInTheDocument()
    expect(screen.getByText('Fallo de red')).toBeInTheDocument()
    expect(screen.queryByText('Aún no tienes conversaciones.')).not.toBeInTheDocument()
  })

  it('creates a group from the header icon with an accessible name', () => {
    const onCreateGroup = vi.fn()
    renderPane({ onCreateGroup })

    const button = screen.getByRole('button', { name: 'Crear grupo' })
    expect(button.closest('.page-header')).not.toBeNull()
    fireEvent.click(button)
    expect(onCreateGroup).toHaveBeenCalledTimes(1)
  })
})
