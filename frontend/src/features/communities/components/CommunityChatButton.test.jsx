import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getCommunityChatRequest = vi.fn()
vi.mock('../services/communities.service', () => ({
  getCommunityChatRequest: (...args) => getCommunityChatRequest(...args),
}))
const navigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

import CommunityChatButton from './CommunityChatButton.jsx'
import { ToastProvider } from '../../../shared/ui/Toast.jsx'

const community = { id: 'c-1', name: 'Trail Vigo', imageUrl: '/media/c.png' }

function renderButton() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <CommunityChatButton community={community} />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('CommunityChatButton (T20)', () => {
  beforeEach(() => {
    getCommunityChatRequest.mockReset()
    navigate.mockReset()
  })

  it('opens the chat of the community in the messages page', async () => {
    getCommunityChatRequest.mockResolvedValue({ conversationId: 'chat-1', enabled: true, canWrite: true })
    renderButton()

    fireEvent.click(screen.getByRole('button', { name: 'Chat' }))

    await waitFor(() => expect(navigate).toHaveBeenCalled())
    expect(getCommunityChatRequest).toHaveBeenCalledWith('c-1')
    expect(navigate).toHaveBeenCalledWith('/messages', {
      state: { selectedConversation: { conversationId: 'chat-1', type: 'GROUP', displayName: 'Trail Vigo', displayImage: '/media/c.png' } },
    })
  })

  it('shows the error and stays when the chat cannot be opened', async () => {
    getCommunityChatRequest.mockRejectedValue(new Error('Comunidad no encontrada'))
    renderButton()

    fireEvent.click(screen.getByRole('button', { name: 'Chat' }))

    expect(await screen.findByText('Comunidad no encontrada')).toBeTruthy()
    expect(navigate).not.toHaveBeenCalled()
  })
})
