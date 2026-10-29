import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CommunityEventsTab, CommunityMembersTab } from './CommunityProfileTabs.jsx'
import { getCommunityMembersPageRequest } from '../../features/communities/services/communities.service'

vi.mock('../../features/communities/services/communities.service', () => ({ getCommunityMembersPageRequest: vi.fn() }))
vi.mock('../../features/events/components/EventCard', () => ({
  default: ({ event }) => <div data-testid="event-card">{event.title}</div>,
}))
vi.mock('../../shared/ui/UserCard', () => ({
  default: ({ username }) => <div data-testid="user-card">{username}</div>,
}))

const renderIn = (ui) => render(<MemoryRouter>{ui}</MemoryRouter>)
const community = { id: 'c1', slug: 'cabras', name: 'Cabras', role: 'MEMBER', memberCount: 3 }
const section = (over = {}) => ({
  items: [], loading: false, loadingMore: false, hasMore: false, error: null, loadMore: vi.fn(), retry: vi.fn(), ...over,
})

beforeEach(() => vi.clearAllMocks())

describe('CommunityEventsTab', () => {
  it('lists the events and lets staff create one', () => {
    renderIn(<CommunityEventsTab community={{ ...community, role: 'MODERATOR' }} section={section({ items: [{ id: 'e1', title: 'Rodaje' }] })} />)
    expect(screen.getByText('Rodaje')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear quedada' })).toBeInTheDocument()
  })

  it('hides "Crear quedada" for plain members and explains when there are no events', () => {
    renderIn(<CommunityEventsTab community={community} section={section()} />)
    expect(screen.getByText('Todavía no hay quedadas programadas')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Crear quedada' })).not.toBeInTheDocument()
  })

  it('shows the error and retries', () => {
    const s = section({ error: 'Sin conexión' })
    renderIn(<CommunityEventsTab community={community} section={s} />)
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(s.retry).toHaveBeenCalled()
  })
})

describe('CommunityMembersTab', () => {
  it('does not ask for the list to someone who is not a member', () => {
    renderIn(<CommunityMembersTab community={{ ...community, role: null }} />)
    expect(screen.getByText('Solo las miembros ven la lista')).toBeInTheDocument()
    expect(getCommunityMembersPageRequest).not.toHaveBeenCalled()
  })

  it('lists the members as rows without a search field up to 20', async () => {
    getCommunityMembersPageRequest.mockResolvedValue({ items: [{ username: 'ana' }, { username: 'bea' }], hasMore: false })
    renderIn(<CommunityMembersTab community={community} />)
    expect(await screen.findAllByTestId('user-card')).toHaveLength(2)
    expect(screen.queryByRole('search')).not.toBeInTheDocument()
  })

  it('offers a search field above 20 members and filters the list', async () => {
    getCommunityMembersPageRequest.mockResolvedValue({ items: [{ username: 'ana' }, { username: 'bea' }], hasMore: false })
    renderIn(<CommunityMembersTab community={{ ...community, memberCount: 21 }} />)
    await screen.findAllByTestId('user-card')
    fireEvent.change(screen.getByLabelText('Buscar miembros'), { target: { value: '@be' } })
    expect(screen.getAllByTestId('user-card')).toHaveLength(1)
    fireEvent.change(screen.getByLabelText('Buscar miembros'), { target: { value: 'zzz' } })
    expect(screen.getByText('Ninguna miembro coincide con la búsqueda.')).toBeInTheDocument()
  })

  it('loads the next page on demand', async () => {
    getCommunityMembersPageRequest
      .mockResolvedValueOnce({ items: [{ username: 'ana' }], hasMore: true })
      .mockResolvedValueOnce({ items: [{ username: 'bea' }], hasMore: false })
    renderIn(<CommunityMembersTab community={community} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Cargar más' }))
    expect(await screen.findByText('bea')).toBeInTheDocument()
    expect(getCommunityMembersPageRequest).toHaveBeenLastCalledWith('c1', 1)
  })
})
