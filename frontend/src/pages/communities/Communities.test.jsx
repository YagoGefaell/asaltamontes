import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../features/communities/services/communities.service.js', () => ({
  getMyCommunitiesRequest: vi.fn(),
  getPendingCommunitiesRequest: vi.fn(),
  getCommunitiesRequest: vi.fn(),
  createCommunityRequest: vi.fn(),
}))
vi.mock('../../shared/components/CommunityJoinButton', () => ({ default: () => <button type="button">Unirse</button> }))
vi.mock('../../shared/components/CommunityRequestButton', () => ({ default: () => <button type="button">Solicitar</button> }))
const navigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (orig) => ({ ...(await orig()), useNavigate: () => navigate }))

import Communities from './Communities.jsx'
import { ToastProvider } from '../../shared/ui/Toast.jsx'
import { UserProfileContext } from '../../features/users/hooks/user.context'
import {
  getMyCommunitiesRequest,
  getPendingCommunitiesRequest,
  getCommunitiesRequest,
} from '../../features/communities/services/communities.service.js'

const page = (items, hasMore = false) => ({ items, hasMore })
const community = (over = {}) => ({
  id: 'c1', name: 'Trail Vigo', slug: 'trail-vigo', role: 'MEMBER', memberCount: 4, imageUrl: null,
  chat: { conversationId: 'chat-1', lastMessage: 'quedamos', lastSender: 'bea', lastMessageAt: '2020-01-01T10:00:00', unreadCount: 0 },
  nextEvent: null,
  ...over,
})

function renderPage(profile = { city: 'Vigo' }) {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <UserProfileContext.Provider value={{ userProfile: profile, setUserProfile: vi.fn(), loading: false }}>
          <Communities />
        </UserProfileContext.Provider>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('Communities: tus comunidades (T29i)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMyCommunitiesRequest.mockResolvedValue(page([]))
    getPendingCommunitiesRequest.mockResolvedValue({ toApprove: [], mine: [] })
    getCommunitiesRequest.mockResolvedValue(page([]))
  })

  it('shows skeleton rows while loading', () => {
    getMyCommunitiesRequest.mockReturnValue(new Promise(() => {}))
    renderPage()
    expect(screen.getAllByTestId('community-skeleton').length).toBeGreaterThan(0)
  })

  it('shows the error with a retry button', async () => {
    getMyCommunitiesRequest.mockRejectedValueOnce(new Error('Error obteniendo tus comunidades'))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Error obteniendo tus comunidades')
    getMyCommunitiesRequest.mockResolvedValueOnce(page([community()]))
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByText('Trail Vigo')).toBeInTheDocument()
  })

  it('lists each community with its last message, unread count and next meetup', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([
      community({
        chat: { conversationId: 'chat-1', lastMessage: 'quedamos', lastSender: 'bea', lastMessageAt: '2020-01-01T10:00:00', unreadCount: 3 },
        nextEvent: { id: 'e1', title: 'Tirada larga', startsAt: '2030-05-04T08:00:00Z', timezone: 'Europe/Madrid' },
      }),
    ]))
    renderPage()
    const link = await screen.findByRole('link', { name: /Trail Vigo/ })
    expect(link).toHaveAttribute('href', '/communities/trail-vigo')
    expect(link).toHaveTextContent('bea: quedamos')
    expect(link).toHaveTextContent('Próxima quedada')
    expect(link.querySelector('button')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Abrir el chat de Trail Vigo, 3 sin leer' }))
    expect(navigate).toHaveBeenCalledWith('/messages', {
      state: { selectedConversation: expect.objectContaining({ conversationId: 'chat-1', displayName: 'Trail Vigo' }) },
    })
  })

  it('does not show text written by a blocked user', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([
      community({ chat: { conversationId: 'chat-1', lastMessage: null, lastSender: null, lastMessageAt: '2020-01-01T10:00:00', unreadCount: 0 } }),
    ]))
    renderPage()
    expect(await screen.findByText('Mensaje de una usuaria bloqueada')).toBeInTheDocument()
  })

  it('hides Pendiente when there is nothing pending', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([community()]))
    renderPage()
    await screen.findByText('Trail Vigo')
    expect(screen.queryByText('Pendiente')).not.toBeInTheDocument()
  })

  it('links the pending items: requests to approve and her own', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([community()]))
    getPendingCommunitiesRequest.mockResolvedValue({
      toApprove: [{ community: { id: 'p1', name: 'Privada', slug: 'privada' }, pendingCount: 2 }],
      mine: [{ id: 'r1', community: { id: 'p2', name: 'Otra', slug: 'otra' } }],
    })
    renderPage()
    expect(await screen.findByText('2 solicitudes por aprobar')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Privada/ })).toHaveAttribute('href', '/communities/privada/settings/requests')
    expect(screen.getByRole('link', { name: /Otra/ })).toHaveAttribute('href', '/communities/otra')
    expect(screen.getByText('Tu solicitud está esperando respuesta')).toBeInTheDocument()
  })

  it('keeps working if the pending request fails', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([community()]))
    getPendingCommunitiesRequest.mockRejectedValue(new Error('boom'))
    renderPage()
    expect(await screen.findByText('Trail Vigo')).toBeInTheDocument()
  })

  it('always links to the search to discover communities, and has no filters', async () => {
    getMyCommunitiesRequest.mockResolvedValue(page([community()]))
    renderPage()
    expect(await screen.findByRole('link', { name: /Descubrir comunidades cerca de ti/ })).toHaveAttribute('href', '/search?tab=communities')
    expect(screen.queryByRole('button', { name: /Filtros/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Provincia')).not.toBeInTheDocument()
  })

  it('with none: empty state, create button and suggestions from her profile city', async () => {
    getCommunitiesRequest.mockResolvedValue(page([
      { id: 's1', name: 'Cabras de Vigo', slug: 'cabras', role: null, memberCount: 9, city: 'Vigo', visibility: 'PUBLIC' },
      { id: 's2', name: 'Ya soy miembro', slug: 'miembro', role: 'MEMBER', memberCount: 3, city: 'Vigo', visibility: 'PUBLIC' },
    ]))
    renderPage({ city: 'Vigo' })
    expect(await screen.findByText('Aún no estás en ninguna comunidad')).toBeInTheDocument()
    expect(await screen.findByText('Cabras de Vigo')).toBeInTheDocument()
    expect(screen.queryByText('Ya soy miembro')).not.toBeInTheDocument()
    expect(getCommunitiesRequest).toHaveBeenCalledWith({ city: 'Vigo' })
    // the + of the header and the empty state's button
    expect(screen.getAllByRole('button', { name: 'Crear comunidad' })).toHaveLength(2)
  })

  it('with none and no city on the profile: no suggestions request', async () => {
    renderPage({ city: '' })
    await screen.findByText('Aún no estás en ninguna comunidad')
    await waitFor(() => expect(getCommunitiesRequest).not.toHaveBeenCalled())
  })
})
