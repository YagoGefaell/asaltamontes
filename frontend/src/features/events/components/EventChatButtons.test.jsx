import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const navigate = vi.fn()
const toast = { error: vi.fn() }
vi.mock('react-router-dom', async (importOriginal) => ({ ...(await importOriginal()), useNavigate: () => navigate }))
vi.mock('../../../shared/ui/Toast.jsx', () => ({ useToast: () => toast }))
vi.mock('../services/events.service.js', () => ({
  getEventChatRequest: vi.fn(),
  getEventCalendarRequest: vi.fn(),
}))

import { getEventCalendarRequest, getEventChatRequest } from '../services/events.service.js'
import AddToCalendarButton from './AddToCalendarButton.jsx'
import EventChatButton from './EventChatButton.jsx'

const event = { id: 'e-1', title: 'Trail nocturno', imageUrl: null }

beforeEach(() => {
  vi.clearAllMocks()
})

describe('EventChatButton', () => {
  it('opens the chat of the event in the messages page', async () => {
    getEventChatRequest.mockResolvedValue({ conversationId: 'chat-1', canWrite: true, readOnlyReason: null })
    render(<MemoryRouter><EventChatButton event={event} /></MemoryRouter>)

    fireEvent.click(screen.getByRole('button', { name: 'Chat de la quedada' }))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/messages', {
      state: { selectedConversation: { conversationId: 'chat-1', type: 'GROUP', displayName: 'Trail nocturno', displayImage: null } },
    }))
    expect(getEventChatRequest).toHaveBeenCalledWith('e-1')
  })

  it('shows the error and stays where it is when the chat cannot be opened', async () => {
    getEventChatRequest.mockRejectedValue(new Error('Chat no encontrado'))
    render(<MemoryRouter><EventChatButton event={event} /></MemoryRouter>)

    fireEvent.click(screen.getByRole('button', { name: 'Chat de la quedada' }))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Chat no encontrado'))
    expect(navigate).not.toHaveBeenCalled()
  })
})

describe('AddToCalendarButton', () => {
  it('saves the calendar file of the event', async () => {
    getEventCalendarRequest.mockResolvedValue('BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n')
    URL.createObjectURL = vi.fn(() => 'blob:ics')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    render(<AddToCalendarButton event={event} />)

    fireEvent.click(screen.getByRole('button', { name: 'Añadir al calendario' }))

    await waitFor(() => expect(click).toHaveBeenCalled())
    expect(getEventCalendarRequest).toHaveBeenCalledWith('e-1')
    expect(URL.createObjectURL.mock.calls[0][0].type).toBe('text/calendar;charset=utf-8')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:ics')
    click.mockRestore()
  })

  it('shows the error when the file cannot be downloaded', async () => {
    getEventCalendarRequest.mockRejectedValue(new Error('Evento no encontrado'))
    render(<AddToCalendarButton event={event} />)

    fireEvent.click(screen.getByRole('button', { name: 'Añadir al calendario' }))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Evento no encontrado'))
  })
})
