import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from './Home.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'
import { getCommunitiesRequest } from '../../features/communities/services/communities.service'
import { getSuggestionsRequest } from '../../features/users/services/users.service'

import { ToastProvider } from '../../shared/ui/Toast'
vi.mock('../../features/communities/services/communities.service', () => ({
  getCommunitiesRequest: vi.fn(),
}))
vi.mock('../../features/users/services/users.service', () => ({ getSuggestionsRequest: vi.fn() }))
vi.mock('../../shared/components/FollowUserButton', () => ({ default: () => <button>Seguir</button> }))
vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ currentUser: { username: 'yo', fullName: 'Yo' } }) }))
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
      <ToastProvider>
        <ConversationsContext.Provider value={{ totalUnreadCount: messages }}>
          <NotificationsContext.Provider value={{ unreadCount: notifications }}>
            <Home />
          </NotificationsContext.Provider>
        </ConversationsContext.Provider>
      </ToastProvider>
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
    expect(document.querySelector('.stories, .post-card')).toBeNull()
  })

  it('previews the composer and says posts are coming soon', async () => {
    renderHome()
    fireEvent.click(screen.getByRole('button', { name: '¿Qué tal tu último entreno?' }))
    expect(await screen.findByText('Las publicaciones llegan pronto')).toBeTruthy()
  })

  it('shows rows of users to follow and communities she is not in, without section titles', async () => {
    getSuggestionsRequest.mockResolvedValue(page([{ username: 'cris', fullName: 'Cris Gil', isFollowing: false }]))
    getCommunitiesRequest.mockResolvedValue(page([
      { id: 'c1', name: 'Cabras', slug: 'cabras', role: 'MEMBER' },
      { id: 'c2', name: 'Valkirias', slug: 'valkirias', city: 'Madrid', role: null },
    ]))
    renderHome()
    expect(await screen.findByRole('link', { name: /Cris Gil/ })).toHaveAttribute('href', '/users/cris')
    expect(await screen.findByRole('link', { name: /Valkirias/ })).toHaveAttribute('href', '/communities/valkirias')
    expect(screen.queryByText('Cabras')).toBeNull()
    expect(document.querySelectorAll('.home-rail')).toHaveLength(2)
    expect(screen.queryByRole('heading', { level: 2 })).toBeNull()
  })
})
