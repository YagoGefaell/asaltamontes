import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FollowRequests from './FollowRequests.jsx'

vi.mock('../../features/users/services/users.service', () => ({
  getFollowRequestsRequest: vi.fn(),
  decideFollowRequest: vi.fn(),
}))
import { decideFollowRequest, getFollowRequestsRequest } from '../../features/users/services/users.service'

const requests = [
  { username: 'ana', fullName: 'Ana Ruiz', profilePictureUrl: null, createdAt: '2026-10-30T10:00:00Z' },
  { username: 'bea', fullName: 'Bea Gil', profilePictureUrl: null, createdAt: '2026-10-30T09:00:00Z' },
]

const renderPage = () => render(<MemoryRouter><FollowRequests /></MemoryRouter>)

beforeEach(() => vi.clearAllMocks())

describe('FollowRequests', () => {
  it('shows the empty state', async () => {
    getFollowRequestsRequest.mockResolvedValue({ items: [], hasMore: false })
    renderPage()
    expect(await screen.findByText('No tienes solicitudes')).toBeTruthy()
  })

  it('accepts a request and removes it from the list', async () => {
    getFollowRequestsRequest.mockResolvedValue({ items: requests, hasMore: false })
    decideFollowRequest.mockResolvedValue()
    renderPage()
    expect(await screen.findByText('Ana Ruiz')).toBeTruthy()

    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0])

    await waitFor(() => expect(decideFollowRequest).toHaveBeenCalledWith('ana', 'approve'))
    await waitFor(() => expect(screen.queryByText('Ana Ruiz')).toBeNull())
    expect(screen.getByText('Bea Gil')).toBeTruthy()
  })

  it('rejects a request', async () => {
    getFollowRequestsRequest.mockResolvedValue({ items: requests, hasMore: false })
    decideFollowRequest.mockResolvedValue()
    renderPage()
    expect(await screen.findByText('Bea Gil')).toBeTruthy()

    fireEvent.click(screen.getAllByRole('button', { name: 'Rechazar' })[1])

    await waitFor(() => expect(decideFollowRequest).toHaveBeenCalledWith('bea', 'reject'))
    await waitFor(() => expect(screen.queryByText('Bea Gil')).toBeNull())
  })

  it('shows the error and keeps the request when the answer fails', async () => {
    getFollowRequestsRequest.mockResolvedValue({ items: requests, hasMore: false })
    decideFollowRequest.mockRejectedValue(new Error('No se ha podido completar la acción'))
    renderPage()
    expect(await screen.findByText('Ana Ruiz')).toBeTruthy()

    fireEvent.click(screen.getAllByRole('button', { name: 'Aceptar' })[0])

    expect((await screen.findByRole('alert')).textContent).toBe('No se ha podido completar la acción')
    expect(screen.getByText('Ana Ruiz')).toBeTruthy()
  })
})
