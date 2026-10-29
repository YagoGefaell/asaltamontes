import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsMenu from './SettingsMenu.jsx'
import { ToastProvider } from '../../shared/ui/Toast.jsx'
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

  it('revokes every session, logs out and goes to the access page after confirming', async () => {
    logoutEverywhereRequest.mockResolvedValue()
    logout.mockResolvedValue()

    render(<ToastProvider><MemoryRouter><SettingsMenu /></MemoryRouter></ToastProvider>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cerrar sesión' }))

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/access'))
    expect(logoutEverywhereRequest).toHaveBeenCalledTimes(1)
    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('does nothing if the user cancels', () => {
    render(<ToastProvider><MemoryRouter><SettingsMenu /></MemoryRouter></ToastProvider>)
    fireEvent.click(screen.getByText('Cerrar sesión en todos los dispositivos'))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(logoutEverywhereRequest).not.toHaveBeenCalled()
    expect(logout).not.toHaveBeenCalled()
  })

  it('only lists entries that lead to an existing screen', () => {
    render(<ToastProvider><MemoryRouter><SettingsMenu /></MemoryRouter></ToastProvider>)
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
