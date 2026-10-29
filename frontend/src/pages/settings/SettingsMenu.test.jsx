import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsMenu from './SettingsMenu.jsx'
import { logoutEverywhereRequest } from '../../features/auth/services/auth.service'

const logout = vi.fn()
const navigate = vi.fn()

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ logout }) }))
vi.mock('../../features/auth/services/auth.service', () => ({ logoutEverywhereRequest: vi.fn() }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

describe('SettingsMenu', () => {
  beforeEach(() => {
    logout.mockReset()
    navigate.mockReset()
    logoutEverywhereRequest.mockReset()
  })

  it('revokes every session, logs out and goes to the access page at once, without asking', async () => {
    logoutEverywhereRequest.mockResolvedValue()
    logout.mockResolvedValue()

    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/access'))
    expect(logoutEverywhereRequest).toHaveBeenCalledTimes(1)
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('logs out at once, without a confirmation', async () => {
    logout.mockResolvedValue()
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión'))

    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/access'))
  })

  it('shows the error in place when closing every session fails', async () => {
    logoutEverywhereRequest.mockRejectedValue(new Error('No se pudo'))
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))

    expect(await screen.findByText('No se pudo')).toBeTruthy()
    expect(logout).not.toHaveBeenCalled()
  })

  it('only lists entries that lead to an existing screen', () => {
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    for (const label of ['Perfil', 'Notificaciones', 'Ajustes de privacidad']) {
      fireEvent.click(screen.getByText(label))
    }
    expect(navigate.mock.calls.map(([path]) => path)).toEqual([
      '/settings/profile', '/settings/notifications', '/settings/privacy',
    ])
    for (const gone of ['Cursos', 'Score en LinkedIn', 'Duolingo for Schools', 'Escoge un plan', 'Centro de ayuda']) {
      expect(screen.queryByText(gone)).toBeNull()
    }
  })
})
