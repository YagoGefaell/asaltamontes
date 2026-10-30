import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from './Home.jsx'
import { NotificationsContext } from '../../features/notifications/hooks/notifications.context'
import { ConversationsContext } from '../../features/messages/hooks/conversations.context'
import { getCommunitiesRequest } from '../../features/communities/services/communities.service'
import { getFeedRequest } from '../../features/posts/services/posts.service'
import { getSuggestionsRequest } from '../../features/users/services/users.service'

vi.mock('../../features/communities/services/communities.service', () => ({
  getCommunitiesRequest: vi.fn(),
}))
vi.mock('../../features/users/services/users.service', () => ({ getSuggestionsRequest: vi.fn() }))
vi.mock('../../features/posts/services/posts.service', () => ({ getFeedRequest: vi.fn() }))
vi.mock('../../features/posts/components/PostCard', () => ({ default: ({ post }) => <article data-testid="post">{post.body}</article> }))
vi.mock('../../shared/components/FollowUserButton', () => ({ default: () => <button>Seguir</button> }))
vi.mock('../../shared/components/CommunityJoinButton', () => ({ default: () => <button>Unirse</button> }))
vi.mock('../../shared/components/CommunityRequestButton', () => ({ default: () => <button>Solicitar</button> }))

const page = (items) => ({ items, hasMore: false, page: 0 })

beforeEach(() => {
  vi.clearAllMocks()
  getCommunitiesRequest.mockResolvedValue(page([]))
  getSuggestionsRequest.mockResolvedValue(page([]))
  getFeedRequest.mockResolvedValue({ items: [], nextBefore: null })
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

describe('Home content (T29i)', () => {
  it('does not repeat the user communities: they live in the Comunidades tab', () => {
    renderHome()
    expect(screen.queryByText('Tus comunidades')).toBeNull()
  })
})

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

describe('Home: crear publicación', () => {
  it('has a + in the header that opens the create screen, and no sheet of options', () => {
    renderHome()
    expect(screen.getByRole('link', { name: 'Nueva publicación' })).toHaveAttribute('href', '/posts/new')
    expect(screen.queryByRole('button', { name: 'Crear' })).toBeNull()
  })
})

describe('Home content', () => {
  it('is for posts: an empty feed with no search shortcut, no events and no made-up posts', async () => {
    renderHome()
    expect(await screen.findByText('Aún no hay publicaciones')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Explorar comunidades' })).toBeNull()
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
    expect(await screen.findByRole('link', { name: /Cris Gil/ })).toHaveAttribute('href', '/users/cris')
    expect(await screen.findByRole('link', { name: /Valkirias/ })).toHaveAttribute('href', '/communities/valkirias')
    expect(screen.queryByText('Cabras')).toBeNull()
    expect(document.querySelectorAll('.home-rail')).toHaveLength(2)
    expect(screen.getByRole('heading', { name: 'Usuarias para seguir' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Comunidades recomendadas' })).toBeTruthy()
  })
})

const posts = (from, to) => Array.from({ length: to - from }, (_, i) => ({ id: `p${from + i}`, body: `Post ${from + i}` }))

describe('Home feed (T22c)', () => {
  const rows = () => [...document.querySelectorAll('.home-section__title')].map((t) => t.textContent)

  beforeEach(() => {
    getSuggestionsRequest.mockResolvedValue(page([{ username: 'cris', fullName: 'Cris Gil' }]))
    getCommunitiesRequest.mockResolvedValue(page([{ id: 'c2', name: 'Valkirias', slug: 'valkirias', role: null }]))
  })

  it('shows the posts and puts a suggestions row after every 5, users first and then communities', async () => {
    getFeedRequest.mockResolvedValue({ items: posts(0, 11), nextBefore: null })
    renderHome()
    expect(await screen.findAllByTestId('post')).toHaveLength(11)
    await screen.findByRole('link', { name: /Valkirias/ })
    const children = [...document.querySelector('.home-container').children].map((el) => el.dataset.testid ?? el.className)
    const layout = children.filter((c) => c === 'post' || c.includes('home-section')).join(' ')
    expect(layout).toBe(['post', 'post', 'post', 'post', 'post', 'home-section', 'post', 'post', 'post', 'post', 'post', 'home-section', 'post'].join(' '))
    expect(rows()).toEqual(['Usuarias para seguir', 'Comunidades recomendadas'])
    expect(screen.queryByText('Aún no hay publicaciones')).toBeNull()
  })

  it('with fewer posts than one row, the rows close the page', async () => {
    getFeedRequest.mockResolvedValue({ items: posts(0, 2), nextBefore: null })
    renderHome()
    expect(await screen.findAllByTestId('post')).toHaveLength(2)
    expect(await screen.findByRole('heading', { name: 'Usuarias para seguir' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Comunidades recomendadas' })).toBeTruthy()
  })

  it('asks for the next page with the cursor on "Cargar más"', async () => {
    getFeedRequest.mockResolvedValueOnce({ items: posts(0, 2), nextBefore: 'c1' })
    getFeedRequest.mockResolvedValueOnce({ items: posts(2, 3), nextBefore: null })
    renderHome()
    fireEvent.click(await screen.findByRole('button', { name: 'Cargar más' }))
    await waitFor(() => expect(screen.getAllByTestId('post')).toHaveLength(3))
    expect(getFeedRequest).toHaveBeenLastCalledWith('c1')
  })

  it('offers to retry when the feed fails', async () => {
    getFeedRequest.mockRejectedValueOnce(new Error('Sin conexión'))
    getFeedRequest.mockResolvedValueOnce({ items: posts(0, 1), nextBefore: null })
    renderHome()
    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión')
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByTestId('post')).toBeTruthy()
  })
})
