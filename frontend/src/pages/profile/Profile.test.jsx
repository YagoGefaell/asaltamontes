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
import { blockUserRequest } from '../../features/users/services/users.service'
vi.mock('../../features/users/components/ProfileFollowButton', () => ({
  default: ({ isPrivate, isFollowRequested }) => <button>{isFollowRequested ? 'Solicitado' : isPrivate ? 'Solicitar' : 'Seguir'}</button>,
}))
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

  it('shows a private profile as locked: lock, request button, no tabs and no lists', () => {
    renderProfile({ userData: { ...data, isPublicProfile: false, canViewContent: false }, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByLabelText('Perfil privado')).toBeTruthy()
    expect(screen.getByText('Este perfil es privado')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Solicitar' })).toBeTruthy()
    expect(screen.queryByRole('tablist')).toBeNull()
    expect(screen.queryByText('tab-eventos')).toBeNull()
    expect(screen.getByRole('button', { name: 'Ver seguidoras' }).disabled).toBe(true)
    // The header stays: name and counters
    expect(screen.getByText('Ana Pérez')).toBeTruthy()
    expect(screen.getByText('12')).toBeTruthy()
  })

  it('shows "Solicitado" while the request waits', () => {
    renderProfile({ userData: { ...data, isPublicProfile: false, canViewContent: false, isFollowRequested: true }, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByRole('button', { name: 'Solicitado' })).toBeTruthy()
  })

  it('keeps the lock icon but opens the tabs for an approved follower', () => {
    renderProfile({ userData: { ...data, isPublicProfile: false, canViewContent: true, isFollowing: true }, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByLabelText('Perfil privado')).toBeTruthy()
    expect(screen.getByText('tab-eventos')).toBeTruthy()
    expect(screen.queryByText('Este perfil es privado')).toBeNull()
  })

  it('blocks at once from the options sheet, without asking, and shows the result in place', async () => {
    blockUserRequest.mockResolvedValue({})
    renderProfile({ userData: data, isOwnProfile: false, onBack: vi.fn() })
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear' }))
    await waitFor(() => expect(blockUserRequest).toHaveBeenCalledWith('ana'))
    expect(await screen.findByText('Has bloqueado a esta usuaria.')).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('keeps the sheet open and shows the error inside when blocking fails', async () => {
    blockUserRequest.mockRejectedValue(new Error('No se pudo bloquear'))
    renderProfile({ userData: data, isOwnProfile: false, onBack: vi.fn() })
    fireEvent.click(screen.getByRole('button', { name: 'Más opciones' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear' }))
    expect(await screen.findByText('No se pudo bloquear')).toBeTruthy()
  })

  it('hides the actions when she is blocked', () => {
    renderProfile({ userData: { ...data, isBlockedByMe: true }, isOwnProfile: false, onBack: vi.fn() })
    expect(screen.getByText('Has bloqueado a esta usuaria.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Mensaje' })).toBeNull()
  })
})
