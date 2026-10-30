import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SecuritySettings from './SecuritySettings.jsx'
import { logoutEverywhereRequest } from '../../features/auth/services/auth.service'

const logout = vi.fn()
const navigate = vi.fn()

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ logout }) }))
vi.mock('../../features/auth/services/auth.service', () => ({ logoutEverywhereRequest: vi.fn() }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

describe('SecuritySettings', () => {
  beforeEach(() => {
    logout.mockReset()
    navigate.mockReset()
    logoutEverywhereRequest.mockReset()
  })

  it('revokes every session, logs out and goes to the access page at once, without asking', async () => {
    logoutEverywhereRequest.mockResolvedValue()
    logout.mockResolvedValue()

    render(<MemoryRouter><SecuritySettings /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))

    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/access'))
    expect(logoutEverywhereRequest).toHaveBeenCalledTimes(1)
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('shows the error in place when closing every session fails', async () => {
    logoutEverywhereRequest.mockRejectedValue(new Error('No se pudo'))
    render(<MemoryRouter><SecuritySettings /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))

    expect(await screen.findByText('No se pudo')).toBeTruthy()
    expect(logout).not.toHaveBeenCalled()
  })
})
