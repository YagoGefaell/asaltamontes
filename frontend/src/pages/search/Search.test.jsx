import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../features/users/services/users.service', () => ({ getAllUsersRequest: vi.fn() }))
vi.mock('../../features/communities/services/communities.service', () => ({ getCommunitiesRequest: vi.fn() }))
vi.mock('../../features/events/services/events.service', () => ({ getEventsRequest: vi.fn() }))
vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ currentUser: { username: 'yo' } }) }))
vi.mock('../../shared/ui/UserCard', () => ({
  default: ({ username }) => <div data-testid="user-row">@{username}</div>,
}))
vi.mock('../../features/search/components/CommunityRow', () => ({
  default: ({ community }) => <div data-testid="community-row">{community.name}</div>,
}))
vi.mock('../../features/events/components/EventCard', () => ({
  default: ({ event }) => <div data-testid="event-row">{event.title}</div>,
}))

import Search from './Search.jsx'
import { getAllUsersRequest } from '../../features/users/services/users.service'
import { getCommunitiesRequest } from '../../features/communities/services/communities.service'
import { getEventsRequest } from '../../features/events/services/events.service'
import { RECENT_KEY } from '../../features/search/hooks/useRecentSearches.js'

const users = (n) => ({ content: Array.from({ length: n }, (_, i) => ({ username: `ana${i}` })), last: true })
const communities = (n) => ({ items: Array.from({ length: n }, (_, i) => ({ id: `c${i}`, name: `Club ${i}` })), hasMore: false, page: 0 })
const events = (n) => ({ items: Array.from({ length: n }, (_, i) => ({ id: `e${i}`, title: `Quedada ${i}` })), hasMore: false, page: 0 })

function renderAt(url = '/search') {
  return render(<MemoryRouter initialEntries={[url]}><Search /></MemoryRouter>)
}

const settle = () => act(async () => { await new Promise((r) => setTimeout(r, 350)) })

describe('Search (T29d)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    getAllUsersRequest.mockResolvedValue(users(5))
    getCommunitiesRequest.mockResolvedValue(communities(5))
    getEventsRequest.mockResolvedValue(events(5))
  })

  it('without text shows suggestions and does not list people', async () => {
    renderAt()
    expect(await screen.findByRole('region', { name: 'Comunidades para ti' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Próximas quedadas' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Comunidades para ti' })).not.toBeInTheDocument()
    expect(getAllUsersRequest).not.toHaveBeenCalled()
    expect(screen.getAllByTestId('community-row')).toHaveLength(5)
  })

  it('suggests only communities she is not in yet', async () => {
    getCommunitiesRequest.mockResolvedValue({
      items: [{ id: 'c1', name: 'Mía', role: 'OWNER' }, { id: 'c2', name: 'Valkirias', role: null }],
      hasMore: false,
      page: 0,
    })
    renderAt()
    expect(await screen.findByText('Valkirias')).toBeInTheDocument()
    expect(screen.queryByText('Mía')).not.toBeInTheDocument()
  })

  it('shows the four tabs', () => {
    renderAt()
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Todo', 'Personas', 'Comunidades', 'Quedadas'])
  })

  it('"Todo" shows a few of each kind with "Ver todas", which opens the tab', async () => {
    renderAt('/search?q=ana')
    await waitFor(() => expect(screen.getAllByTestId('user-row')).toHaveLength(3))
    expect(screen.getAllByTestId('community-row')).toHaveLength(3)
    expect(screen.getAllByTestId('event-row')).toHaveLength(3)
    fireEvent.click(screen.getAllByRole('button', { name: 'Ver todas' })[0])
    expect(screen.getByRole('tab', { name: 'Personas' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getAllByTestId('user-row')).toHaveLength(5)
    expect(screen.queryByTestId('community-row')).not.toBeInTheDocument()
  })

  it('opens the tab given in the URL and searches only that kind', async () => {
    renderAt('/search?tab=communities&q=trail')
    await waitFor(() => expect(screen.getAllByTestId('community-row')).toHaveLength(5))
    expect(screen.getByRole('tab', { name: 'Comunidades' })).toHaveAttribute('aria-selected', 'true')
    expect(getAllUsersRequest).not.toHaveBeenCalled()
    expect(getEventsRequest).not.toHaveBeenCalled()
    expect(getCommunitiesRequest).toHaveBeenCalledWith(expect.objectContaining({ query: 'trail' }))
  })

  it('searches events by text (title, city or place) with q', async () => {
    renderAt('/search?tab=events&q=Madrid')
    await waitFor(() => expect(getEventsRequest).toHaveBeenCalledWith(expect.objectContaining({ q: 'Madrid' })))
  })

  it('searches after she stops typing and remembers the term on submit', async () => {
    renderAt()
    const input = screen.getByRole('searchbox', { name: 'Buscar' })
    fireEvent.change(input, { target: { value: 'laura' } })
    await settle()
    expect(getAllUsersRequest).toHaveBeenCalledWith(0, 20, 'laura')
    fireEvent.submit(input.closest('form'))
    expect(JSON.parse(window.localStorage.getItem(RECENT_KEY))).toEqual(['laura'])
  })

  it('hides recents until she focuses the field, and then shows them instead of the suggestions', async () => {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(['marta']))
    renderAt()
    expect(await screen.findByRole('region', { name: 'Comunidades para ti' })).toBeInTheDocument()
    expect(screen.queryByText('Recientes')).not.toBeInTheDocument()
    fireEvent.focus(screen.getByRole('searchbox'))
    expect(screen.getByText('Recientes')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Comunidades para ti' })).not.toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('"Cancelar" clears the text and goes back to the suggestions', async () => {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(['marta']))
    renderAt('/search?q=ana')
    const input = screen.getByRole('searchbox')
    fireEvent.focus(input)
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(input).toHaveValue('')
    expect(screen.queryByText('Recientes')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Comunidades para ti' })).toBeInTheDocument()
  })

  it('says there are no recents when she focuses the field without history', () => {
    renderAt()
    fireEvent.focus(screen.getByRole('searchbox'))
    expect(screen.getByText('No hay búsquedas recientes.')).toBeInTheDocument()
  })

  it('lists recents on focus, reuses one and removes another', async () => {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(['marta', 'laura']))
    renderAt()
    fireEvent.focus(screen.getByRole('searchbox'))
    expect(screen.getByText('Recientes')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Quitar laura de recientes' }))
    expect(screen.queryByText('laura')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'marta' }))
    expect(screen.getByRole('searchbox')).toHaveValue('marta')
    await waitFor(() => expect(getAllUsersRequest).toHaveBeenCalledWith(0, 20, 'marta'))
  })

  it('does not break when localStorage is unavailable', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    renderAt()
    expect(screen.getByRole('searchbox')).toBeInTheDocument()
    spy.mockRestore()
  })

  it('shows an empty state when nothing matches', async () => {
    getAllUsersRequest.mockResolvedValue(users(0))
    getCommunitiesRequest.mockResolvedValue(communities(0))
    getEventsRequest.mockResolvedValue(events(0))
    renderAt('/search?q=zzz')
    expect(await screen.findByText('No hay resultados para «zzz»')).toBeInTheDocument()
  })

  it('shows skeletons while loading', () => {
    getAllUsersRequest.mockReturnValue(new Promise(() => {}))
    renderAt('/search?tab=users&q=ana')
    expect(screen.getAllByTestId('search-skeleton').length).toBeGreaterThan(0)
  })

  it('shows an error with "Reintentar" that asks again', async () => {
    getAllUsersRequest.mockRejectedValueOnce(new Error('Error obteniendo usuarios'))
    renderAt('/search?tab=users&q=ana')
    expect(await screen.findByRole('alert')).toHaveTextContent('Error obteniendo usuarios')
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(screen.getAllByTestId('user-row')).toHaveLength(5))
  })
})
