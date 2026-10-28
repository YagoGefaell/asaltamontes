import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from './Home.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'
import { getCommunitiesRequest } from '../../features/communities/services/communities.service'
import { getSuggestionsRequest } from '../../features/users/services/users.service'

vi.mock('../../features/communities/services/communities.service', () => ({
  getCommunitiesRequest: vi.fn(),
}))
vi.mock('../../features/users/services/users.service', () => ({ getSuggestionsRequest: vi.fn() }))
vi.mock('../../shared/components/FollowUserButton', () => ({ default: () => <button>Seguir</button> }))
vi.mock('../../shared/components/CommunityJoinButton', () => ({ default: () => <button>Unirse</button> }))
vi.mock('../../shared/components/CommunityRequestButton', () => ({ default: () => <button>Solicitar</button> }))

const page = (items) => ({ items, hasMore: false, page: 0 })

beforeEach(() => {
  vi.clearAllMocks()
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
  it('is for posts: an empty feed with an action, no events and no made-up posts', async () => {
    renderHome()
    expect(await screen.findByText('Aún no hay publicaciones')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Explorar comunidades' })).toBeTruthy()
    expect(screen.queryByText(/quedadas/i)).toBeNull()
    expect(document.querySelector('.stories, .post-card, .home-composer')).toBeNull()
  })

  it('shows titled rows of users to follow and communities she is not in', async () => {
    getSuggestionsRequest.mockResolvedValue(page([{ username: 'cris', fullName: 'Cris Gil', isFollowing: false }]))
    getCommunitiesRequest.mockResolvedValue(page([
      { id: 'c1', name: 'Cabras', slug: 'cabras', role: 'MEMBER' },
      { id: 'c2', name: 'Valkirias', slug: 'valkirias', city: 'Madrid', role: null },
    ]))
    renderHome()
    expect(await screen.findByRole('link', { name: 'Cris Gil (@cris)' })).toHaveAttribute('href', '/users/cris')
    // Only the username is shown, as on Instagram
    expect(screen.getByText('cris')).toBeTruthy()
    expect(screen.queryByText('Cris Gil')).toBeNull()
    expect(await screen.findByRole('link', { name: /Valkirias/ })).toHaveAttribute('href', '/communities/valkirias')
    expect(screen.queryByText('Cabras')).toBeNull()
    expect(document.querySelectorAll('.home-rail')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Sugerencias para ti' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Comunidades para ti' })).toBeTruthy()
  })
})
