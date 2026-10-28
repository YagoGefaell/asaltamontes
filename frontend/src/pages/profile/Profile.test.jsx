import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Profile from './Profile.jsx'

const navigate = vi.fn()
vi.mock('react-router-dom', async (orig) => ({ ...(await orig()), useNavigate: () => navigate }))
vi.mock('../../features/auth/hooks/useAuth', () => ({
  useAuth: () => ({ currentUser: { username: 'yo', followersCount: 7, followingCount: 3 }, setCurrentUser: vi.fn() }),
}))
vi.mock('../../features/users/hooks/useUserProfile', () => ({ useUserProfile: () => ({ userProfile: null, loading: false }) }))
vi.mock('../../features/users/services/users.service', () => ({ blockUserRequest: vi.fn(), unblockUserRequest: vi.fn() }))
vi.mock('../../shared/components/FollowUserButton', () => ({ default: () => <button>Seguir</button> }))
vi.mock('../../shared/ui/Toast', () => ({ useToast: () => ({ success: vi.fn(), error: vi.fn() }) }))
vi.mock('./ProfileTabs', () => ({
  ProfileEvents: () => <div>tab-eventos</div>,
  ProfileCommunities: () => <div>tab-comunidades</div>,
}))

const data = {
  username: 'ana',
  fullName: 'Ana Pérez',
  bio: 'Corro por el Moncayo',
  city: 'Zaragoza',
  runningLevel: 'INTERMEDIATE',
  followersCount: 12,
  followingCount: 5,
}

const renderProfile = (props) => render(<MemoryRouter><Profile {...props} /></MemoryRouter>)

beforeEach(() => navigate.mockClear())

describe('Profile (own)', () => {
  it('shows the Instagram-style header, no filler text and the content tabs', () => {
    renderProfile({ userData: data, isOwnProfile: true })
    expect(screen.getByText('@ana')).toBeTruthy()
    expect(screen.getByText('Ana Pérez')).toBeTruthy()
    expect(screen.getByText('Corro por el Moncayo')).toBeTruthy()
    expect(screen.getByText('Intermedia')).toBeTruthy()
    expect(screen.queryByText(/Aquí podrías/)).toBeNull()
    expect(screen.getByRole('button', { name: 'Editar perfil' })).toBeTruthy()
    expect(screen.getByText('tab-eventos')).toBeTruthy()
    fireEvent.click(screen.getByRole('tab', { name: 'Comunidades' }))
    expect(screen.getByText('tab-comunidades')).toBeTruthy()
  })

  it('uses the counters of the session and links them to the lists', () => {
    renderProfile({ userData: data, isOwnProfile: true })
    expect(screen.getByText('7')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Ver seguidoras' }))
    expect(navigate).toHaveBeenCalledWith('/me/followers')
    fireEvent.click(screen.getByRole('button', { name: 'Editar perfil' }))
    expect(navigate).toHaveBeenCalledWith('/settings/profile')
  })

  it('hides the bio line when she has none', () => {
    renderProfile({ userData: { ...data, bio: '' }, isOwnProfile: true })
    expect(screen.queryByText(/Agrega una bio/)).toBeNull()
    expect(screen.queryByText('Sin biografía')).toBeNull()
  })
})

describe('Profile (someone else)', () => {
  it('offers follow and message, and shows the tabs of what she organizes and her public communities', () => {
    renderProfile({ userData: data, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByRole('button', { name: 'Seguir' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Mensaje' })).toBeTruthy()
    expect(screen.getByRole('tablist')).toBeTruthy()
    expect(screen.getByText('tab-eventos')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Ver seguidoras' }))
    expect(navigate).toHaveBeenCalledWith('/users/ana/followers')
  })

  it('asks for confirmation from the options sheet before blocking', async () => {
    renderProfile({ userData: data, isOwnProfile: false, onBack: vi.fn() })
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    const options = await screen.findAllByRole('button', { name: 'Bloquear' })
    fireEvent.click(options[0])
    await waitFor(() => expect(screen.getByText(/¿Bloquear a @ana\?/)).toBeTruthy())
  })

  it('hides the actions when she is blocked', () => {
    renderProfile({ userData: { ...data, isBlockedByMe: true }, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByText('Has bloqueado a esta usuaria.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Mensaje' })).toBeNull()
  })
})
