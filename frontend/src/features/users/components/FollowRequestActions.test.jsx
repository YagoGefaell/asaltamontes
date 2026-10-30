import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FollowRequestActions from './FollowRequestActions.jsx'

const incrementFollowers = vi.fn()
vi.mock('../../auth/hooks/useAuth', () => ({ useAuth: () => ({ incrementFollowers }) }))
vi.mock('../services/users.service', () => ({ decideFollowRequest: vi.fn() }))
import { decideFollowRequest } from '../services/users.service'

beforeEach(() => vi.clearAllMocks())

describe('FollowRequestActions', () => {
  it('shows two distinct buttons', () => {
    render(<FollowRequestActions username="ana" />)
    expect(screen.getByRole('button', { name: 'Aceptar la solicitud de ana' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Rechazar la solicitud de ana' })).toBeTruthy()
  })

  it('accepts in place and counts one more follower', async () => {
    decideFollowRequest.mockResolvedValue(true)
    render(<FollowRequestActions username="ana" />)

    fireEvent.click(screen.getByRole('button', { name: 'Aceptar la solicitud de ana' }))

    expect((await screen.findByRole('status')).textContent).toBe('Solicitud aceptada')
    expect(decideFollowRequest).toHaveBeenCalledWith('ana', 'approve')
    expect(incrementFollowers).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('rejects in place without touching the counter', async () => {
    decideFollowRequest.mockResolvedValue(true)
    render(<FollowRequestActions username="ana" />)

    fireEvent.click(screen.getByRole('button', { name: 'Rechazar la solicitud de ana' }))

    expect((await screen.findByRole('status')).textContent).toBe('Solicitud rechazada')
    expect(decideFollowRequest).toHaveBeenCalledWith('ana', 'reject')
    expect(incrementFollowers).not.toHaveBeenCalled()
  })

  it('says so when the request no longer exists, without counting', async () => {
    decideFollowRequest.mockResolvedValue(false)
    render(<FollowRequestActions username="ana" />)

    fireEvent.click(screen.getByRole('button', { name: 'Aceptar la solicitud de ana' }))

    expect((await screen.findByRole('status')).textContent).toBe('Solicitud ya resuelta')
    expect(incrementFollowers).not.toHaveBeenCalled()
  })

  it('shows the error inline and keeps the buttons to retry', async () => {
    decideFollowRequest.mockRejectedValue(new Error('No se ha podido completar la acción'))
    render(<FollowRequestActions username="ana" />)

    fireEvent.click(screen.getByRole('button', { name: 'Aceptar la solicitud de ana' }))

    expect((await screen.findByRole('alert')).textContent).toBe('No se ha podido completar la acción')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Aceptar la solicitud de ana' }).disabled).toBe(false))
  })
})
