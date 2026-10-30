import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfileFollowButton from './ProfileFollowButton.jsx'

const incrementFollowing = vi.fn()
const decrementFollowing = vi.fn()
vi.mock('../../auth/hooks/useAuth', () => ({ useAuth: () => ({ incrementFollowing, decrementFollowing }) }))
vi.mock('../services/users.service', () => ({ followUserRequest: vi.fn(), unfollowUserRequest: vi.fn() }))
import { followUserRequest, unfollowUserRequest } from '../services/users.service'

beforeEach(() => vi.clearAllMocks())

describe('ProfileFollowButton', () => {
  it('follows a public profile at once', async () => {
    followUserRequest.mockResolvedValue({ status: 'FOLLOWING' })
    render(<ProfileFollowButton username="ana" />)

    fireEvent.click(screen.getByRole('button', { name: 'Seguir a ana' }))

    expect(await screen.findByRole('button', { name: 'Dejar de seguir a ana' })).toBeTruthy()
    expect(incrementFollowing).toHaveBeenCalledTimes(1)
  })

  it('asks to follow a private profile, shows "Solicitado" and does not count a follow', async () => {
    followUserRequest.mockResolvedValue({ status: 'REQUESTED' })
    render(<ProfileFollowButton username="ana" isPrivate />)
    expect(screen.getByText('Solicitar')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Solicitar seguir a ana' }))

    expect(await screen.findByText('Solicitado')).toBeTruthy()
    expect(incrementFollowing).not.toHaveBeenCalled()
  })

  it('withdraws the request without touching the following counter', async () => {
    unfollowUserRequest.mockResolvedValue(null)
    render(<ProfileFollowButton username="ana" isPrivate isFollowRequested />)

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar la solicitud a ana' }))

    await waitFor(() => expect(unfollowUserRequest).toHaveBeenCalledWith('ana'))
    expect(await screen.findByText('Solicitar')).toBeTruthy()
    expect(decrementFollowing).not.toHaveBeenCalled()
  })

  it('unfollows and decrements the counter', async () => {
    unfollowUserRequest.mockResolvedValue(null)
    render(<ProfileFollowButton username="ana" isFollowing />)

    fireEvent.click(screen.getByRole('button', { name: 'Dejar de seguir a ana' }))

    expect(await screen.findByText('Seguir')).toBeTruthy()
    expect(decrementFollowing).toHaveBeenCalledTimes(1)
  })

  it('shows the error and keeps the state when the request fails', async () => {
    followUserRequest.mockRejectedValue('Error al seguir a la usuaria')
    render(<ProfileFollowButton username="ana" isPrivate />)

    fireEvent.click(screen.getByRole('button', { name: 'Solicitar seguir a ana' }))

    expect((await screen.findByRole('alert')).textContent).toBe('Error al seguir a la usuaria')
    expect(screen.getByText('Solicitar')).toBeTruthy()
  })
})
