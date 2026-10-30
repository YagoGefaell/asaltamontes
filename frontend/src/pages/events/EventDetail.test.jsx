import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../features/auth/hooks/useAuth.js', () => ({ useAuth: vi.fn() }))
vi.mock('../../features/events/hooks/useEvent.js', () => ({ useEvent: vi.fn() }))
vi.mock('../../features/events/hooks/useEventAttendance.js', () => ({
  useEventAttendance: () => ({ pending: false, join: vi.fn(), leave: vi.fn() }),
}))
vi.mock('../../features/events/services/events.service.js', () => ({
  getEventAttendeesRequest: vi.fn(),
  getEventChatRequest: vi.fn(),
  getEventCalendarRequest: vi.fn(),
}))
vi.mock('../../features/events/components/EventMap.jsx', () => ({ default: () => <div data-testid="map" /> }))

import { useAuth } from '../../features/auth/hooks/useAuth.js'
import { useEvent } from '../../features/events/hooks/useEvent.js'
import { getEventAttendeesRequest } from '../../features/events/services/events.service.js'
import EventDetail from './EventDetail.jsx'

const event = {
  id: 'e-1', title: 'Trail nocturno', type: 'TRAIL', level: 'INTERMEDIATE', startsAt: '2026-11-07T17:00:00Z',
  timezone: 'Europe/Madrid', attendeeCount: 3, capacity: 10, status: 'OPEN', myStatus: null, imageUrl: null,
  latitude: 40.4, longitude: -3.7, organizerUsername: 'otra', organizerFullName: 'Otra', canEdit: false,
}

function renderPage(overrides = {}, username = 'ana') {
  useAuth.mockReturnValue({ currentUser: { username } })
  useEvent.mockReturnValue({ event: { ...event, ...overrides }, setEvent: vi.fn(), loading: false, error: null, refetch: vi.fn() })
  return render(
    <MemoryRouter initialEntries={['/events/e-1']}>
      <Routes><Route path="events/:id" element={<EventDetail />} /></Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  getEventAttendeesRequest.mockResolvedValue({
    items: [{ username: 'u1', fullName: 'Uno' }, { username: 'u2', fullName: 'Dos' }], hasMore: false, page: 0,
  })
})

describe('EventDetail (T29m)', () => {
  it('has the title in the header and a single main button', async () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1, name: 'Trail nocturno' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apuntarme' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Chat de la quedada' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Añadir al calendario' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Compartir la quedada' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Cómo llegar/ })).toHaveAttribute('href', expect.stringContaining('40.4,-3.7'))
    await waitFor(() => expect(getEventAttendeesRequest).toHaveBeenCalled())
  })

  it('shows chat only when she is going and puts all the metadata in one line', async () => {
    renderPage({ myStatus: 'GOING' })
    expect(screen.getByRole('button', { name: 'Chat de la quedada' })).toBeInTheDocument()
    expect(screen.getByText(/·.*3\/10/)).toBeInTheDocument()
    await waitFor(() => expect(getEventAttendeesRequest).toHaveBeenCalled())
  })

  it('shows the organizer as a label, not as a disabled button', async () => {
    renderPage({ organizerUsername: 'ana', myStatus: 'GOING', canEdit: true }, 'ana')
    expect(screen.getByText('Eres la organizadora')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /organizadora/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Apuntarme' })).not.toBeInTheDocument()
    await waitFor(() => expect(getEventAttendeesRequest).toHaveBeenCalled())
  })

  it('shows a cover only when the event has an image', async () => {
    const { container, unmount } = renderPage()
    expect(container.querySelector('.event-detail__cover')).toBeNull()
    await waitFor(() => expect(getEventAttendeesRequest).toHaveBeenCalled())
    unmount()
    const second = renderPage({ imageUrl: '/media/e.jpg' })
    expect(second.container.querySelector('.event-detail__cover')).toHaveAttribute('src', '/media/e.jpg')
    await waitFor(() => expect(getEventAttendeesRequest).toHaveBeenCalled())
  })

  it('puts the action row below the map', async () => {
    renderPage()
    const map = await screen.findByTestId('map')
    const actions = screen.getByRole('group', { name: 'Acciones de la quedada' })
    expect(map.compareDocumentPosition(actions) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('lists the attendees as stacked avatars with "Ver todas"', async () => {
    renderPage()
    expect(await screen.findByRole('button', { name: /Ver todas las asistentes/ })).toBeInTheDocument()
  })
})
