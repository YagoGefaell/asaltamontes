import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => ({ ...(await importOriginal()), useNavigate: () => navigate }))
vi.mock('../services/events.service.js', () => ({
  getEventChatRequest: vi.fn(),
  getEventCalendarRequest: vi.fn(),
}))

import { getEventCalendarRequest, getEventChatRequest } from '../services/events.service.js'
import AddToCalendarButton from './AddToCalendarButton.jsx'
import EventChatButton from './EventChatButton.jsx'
import ShareEventButton from './ShareEventButton.jsx'

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
    const onError = vi.fn()
    render(<MemoryRouter><EventChatButton event={event} onError={onError} /></MemoryRouter>)

    fireEvent.click(screen.getByRole('button', { name: 'Chat de la quedada' }))

    await waitFor(() => expect(onError).toHaveBeenLastCalledWith('Chat no encontrado'))
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
    const onError = vi.fn()
    render(<AddToCalendarButton event={event} onError={onError} />)

    fireEvent.click(screen.getByRole('button', { name: 'Añadir al calendario' }))

    await waitFor(() => expect(onError).toHaveBeenLastCalledWith('Evento no encontrado'))
  })
})

describe('ShareEventButton', () => {
  afterEach(() => {
    delete navigator.share
    delete navigator.clipboard
  })

  it('uses the Web Share API when the browser has it', async () => {
    navigator.share = vi.fn().mockResolvedValue()
    render(<ShareEventButton event={event} />)

    fireEvent.click(screen.getByRole('button', { name: 'Compartir la quedada' }))

    await waitFor(() => expect(navigator.share).toHaveBeenCalledWith({
      title: 'Trail nocturno', url: `${window.location.origin}/events/e-1`,
    }))
  })

  it('copies the link and says so in place when there is no Web Share API', async () => {
    const writeText = vi.fn().mockResolvedValue()
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    render(<ShareEventButton event={event} />)

    fireEvent.click(screen.getByRole('button', { name: 'Compartir la quedada' }))

    expect(await screen.findByText('Enlace copiado')).toBeTruthy()
    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/events/e-1`)
  })

  it('does not report an error when she closes the share sheet', async () => {
    navigator.share = vi.fn().mockRejectedValue(Object.assign(new Error('x'), { name: 'AbortError' }))
    const onError = vi.fn()
    render(<ShareEventButton event={event} onError={onError} />)

    fireEvent.click(screen.getByRole('button', { name: 'Compartir la quedada' }))

    await waitFor(() => expect(navigator.share).toHaveBeenCalled())
    expect(onError).not.toHaveBeenCalledWith(expect.stringContaining('No se ha podido'))
  })
})
