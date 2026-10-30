import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../features/communities/services/communities.service.js', () => ({ createCommunityRequest: vi.fn() }))
const navigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async (orig) => ({ ...(await orig()), useNavigate: () => navigate }))

import CommunityCreate from './CommunityCreate.jsx'
import { createCommunityRequest } from '../../features/communities/services/communities.service.js'

const renderPage = () => render(<MemoryRouter><CommunityCreate /></MemoryRouter>)

describe('CommunityCreate (/communities/new)', () => {
  beforeEach(() => vi.clearAllMocks())

  it('goes straight into the community she has just created, with no notice', async () => {
    createCommunityRequest.mockResolvedValue({ id: 'c9', slug: 'nueva', name: 'Nueva' })
    renderPage()
    fireEvent.change(screen.getByLabelText(/Nombre/), { target: { value: 'Nueva' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear comunidad' }))
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/communities/nueva', { replace: true }))
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('shows the failure in place and stays on the form', async () => {
    createCommunityRequest.mockRejectedValue(new Error('Ya existe una comunidad con ese nombre'))
    renderPage()
    fireEvent.change(screen.getByLabelText(/Nombre/), { target: { value: 'Nueva' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear comunidad' }))
    expect(await screen.findByText('Ya existe una comunidad con ese nombre')).toBeInTheDocument()
    expect(navigate).not.toHaveBeenCalled()
  })

  it('goes back with the arrow', () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Volver' }))
    expect(navigate).toHaveBeenCalledWith(-1)
  })
})
