import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from './Home.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'
import { getEventsRequest } from '../../features/events/services/events.service'
import { getCommunitiesRequest } from '../../features/communities/services/communities.service'
import { getSuggestionsRequest } from '../../features/users/services/users.service'

vi.mock('../../features/events/services/events.service', () => ({ getEventsRequest: vi.fn() }))
vi.mock('../../features/communities/services/communities.service', () => ({
  getCommunitiesRequest: vi.fn(),
}))
vi.mock('../../features/users/services/users.service', () => ({ getSuggestionsRequest: vi.fn() }))
vi.mock('../../shared/components/FollowUserButton', () => ({ default: () => <button>Seguir</button> }))
vi.mock('../../features/events/components/EventCard', () => ({
  default: ({ event }) => <div data-testid="event-card">{event.title}</div>,
}))
vi.mock('../../shared/components/CommunityJoinButton', () => ({ default: () => <button>Unirse</button> }))
vi.mock('../../shared/components/CommunityRequestButton', () => ({ default: () => <button>Solicitar</button> }))

const page = (items) => ({ items, hasMore: false, page: 0 })

beforeEach(() => {
  vi.clearAllMocks()
  getEventsRequest.mockResolvedValue(page([]))
  getCommunitiesRequest.mockResolvedValue(page([]))
  getSuggestionsRequest.mockResolvedValue(page([]))
})

function renderHome({ notifications = 0, messages = 0 } = {}) {
  return render(
    <MemoryRouter>
      <ConversationsContext.Provider value={{ totalUnreadCount: messages }}>
        <NotificationsContext.Provider value={{ unreadCount: notifications }}>
          <Home />
        </NotificationsContext.Provider>
      </ConversationsContext.Provider>
    </MemoryRouter>,
  )
}

describe('Home header', () => {
  it('links to notifications and messages without counters at zero', () => {
    renderHome()
    expect(screen.getByRole('link', { name: 'Notificaciones' })).toHaveAttribute('href', '/notifications')
    expect(screen.getByRole('link', { name: 'Mensajes' })).toHaveAttribute('href', '/messages')
    expect(document.querySelector('.home-header__badge')).toBeNull()
  })

  it('shows each counter with an accessible label', () => {
    renderHome({ notifications: 4, messages: 2 })
    expect(screen.getByRole('link', { name: 'Notificaciones, 4 sin leer' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Mensajes, 2 sin leer' })).toBeTruthy()
  })

  it('caps counters at 9+', () => {
    renderHome({ notifications: 10, messages: 25 })
    const badges = [...document.querySelectorAll('.home-header__badge')].map((b) => b.textContent)
    expect(badges).toEqual(['9+', '9+'])
  })
})

describe('Home content', () => {
  it('shows an empty state with an action and no made-up posts when there is nothing yet', async () => {
    renderHome()
    expect(await screen.findByText('Todavía no hay quedadas a la vista')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ver quedadas' })).toBeTruthy()
    expect(document.querySelector('.stories, .post-card')).toBeNull()
    expect(screen.queryByText(/likes/)).toBeNull()
  })

  it('lists the events she is going to once and the other upcoming ones without repeating', async () => {
    getEventsRequest.mockImplementation(async (filters) =>
      filters.followedBy ? page([]) : filters.mine
        ? page([{ id: 'a', title: 'Trail del Montseny' }])
        : page([{ id: 'a', title: 'Trail del Montseny' }, { id: 'b', title: 'Sunset run' }]),
    )
    renderHome()
    expect(await screen.findByText('Tus próximas quedadas')).toBeTruthy()
    await waitFor(() => expect(screen.getAllByTestId('event-card')).toHaveLength(2))
    expect(screen.getAllByText('Trail del Montseny')).toHaveLength(1)
    expect(screen.getByText('Sunset run')).toBeTruthy()
  })

  it('does not list her communities and recommends only the ones she is not in, in a row', async () => {
    getCommunitiesRequest.mockResolvedValue(page([
      { id: 'c1', name: 'Cabras', slug: 'cabras', role: 'MEMBER' },
      { id: 'c2', name: 'Valkirias', slug: 'valkirias', city: 'Madrid', role: null },
    ]))
    renderHome()
    expect(await screen.findByText('Comunidades recomendadas')).toBeTruthy()
    expect(screen.queryByText('Tus comunidades')).toBeNull()
    expect(screen.queryByText('Cabras')).toBeNull()
    expect(screen.getByRole('link', { name: /Valkirias/ })).toHaveAttribute('href', '/communities/valkirias')
    expect(document.querySelector('.home-rail')).not.toBeNull()
  })

  it('shows the events of the people she follows and a row of users to follow', async () => {
    getEventsRequest.mockImplementation(async (filters) =>
      filters.followedBy ? page([{ id: 'f', title: 'Rodada de Bea' }]) : page([]))
    getSuggestionsRequest.mockResolvedValue(page([{ username: 'cris', fullName: 'Cris Gil', isFollowing: false }]))
    renderHome()
    expect(await screen.findByText('Quedadas de quienes sigues')).toBeTruthy()
    expect(screen.getByText('Rodada de Bea')).toBeTruthy()
    expect(await screen.findByText('Usuarias para seguir')).toBeTruthy()
    expect(screen.getByRole('link', { name: /Cris Gil/ })).toHaveAttribute('href', '/users/cris')
    expect(getEventsRequest).toHaveBeenCalledWith(expect.objectContaining({ followedBy: true }))
  })

  it('offers a retry when the events fail to load', async () => {
    getEventsRequest.mockRejectedValue(new Error('boom'))
    renderHome()
    expect(await screen.findByText('No hemos podido cargar las quedadas.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeTruthy()
  })
})
