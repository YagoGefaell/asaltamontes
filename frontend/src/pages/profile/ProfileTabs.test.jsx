import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProfileCommunities, ProfileEvents } from './ProfileTabs.jsx'
import { getEventsRequest } from '../../features/events/services/events.service'
import { getMyCommunitiesRequest } from '../../features/communities/services/communities.service'

vi.mock('../../features/events/services/events.service', () => ({ getEventsRequest: vi.fn() }))
vi.mock('../../features/communities/services/communities.service', () => ({ getMyCommunitiesRequest: vi.fn() }))
vi.mock('../../features/events/components/EventCard', () => ({
  default: ({ event }) => <div data-testid="event-card">{event.title}</div>,
}))
vi.mock('../../features/search/components/CommunityRow', () => ({
  default: ({ community }) => <div data-testid="community-row">{community.name}</div>,
}))

const page = (items) => ({ items, hasMore: false, page: 0 })
const renderIn = (ui) => render(<MemoryRouter>{ui}</MemoryRouter>)

beforeEach(() => vi.clearAllMocks())

describe('ProfileEvents', () => {
  it('asks for her upcoming events and lists them', async () => {
    getEventsRequest.mockResolvedValue(page([{ id: 'a', title: 'Trail del Montseny' }]))
    renderIn(<ProfileEvents />)
    expect(await screen.findByText('Trail del Montseny')).toBeTruthy()
    expect(getEventsRequest).toHaveBeenCalledWith(expect.objectContaining({ mine: true, page: 0 }))
  })

  it('shows an empty state with one action', async () => {
    getEventsRequest.mockResolvedValue(page([]))
    renderIn(<ProfileEvents />)
    expect(await screen.findByText('Aún no tienes quedadas')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ver quedadas' })).toBeTruthy()
  })

  it('shows the error with a retry', async () => {
    getEventsRequest.mockRejectedValue(new Error('Fallo de red'))
    renderIn(<ProfileEvents />)
    expect(await screen.findByText('Fallo de red')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeTruthy()
  })
})

describe('ProfileCommunities', () => {
  it('lists her communities', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([{ id: 'c1', name: 'Cabras' }]))
    renderIn(<ProfileCommunities />)
    expect(await screen.findByText('Cabras')).toBeTruthy()
  })

  it('shows an empty state with one action', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([]))
    renderIn(<ProfileCommunities />)
    expect(await screen.findByText('Aún no estás en ninguna comunidad')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Descubrir comunidades' })).toBeTruthy()
  })
})
