import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SettingsMenu from './SettingsMenu.jsx'

const logout = vi.fn()
const navigate = vi.fn()

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ logout }) }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

describe('SettingsMenu', () => {
  beforeEach(() => {
    logout.mockReset()
    navigate.mockReset()
  })

  it('logs out at once, without a confirmation', async () => {
    logout.mockResolvedValue()
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    fireEvent.click(screen.getByText('Cerrar sesión'))

    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/access'))
  })

  it('has no logout-everywhere here: it lives in Seguridad', () => {
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    expect(screen.queryByText('Cerrar sesión en todos los dispositivos')).toBeNull()
    expect(screen.getByText('Cerrar sesión').closest('button')).toHaveClass('logout-button')
  })

  it('only lists entries that lead to an existing screen', () => {
    render(<MemoryRouter><SettingsMenu /></MemoryRouter>)
    for (const label of ['Perfil', 'Notificaciones', 'Ajustes de privacidad', 'Seguridad']) {
      fireEvent.click(screen.getByText(label))
    }
    expect(navigate.mock.calls.map(([path]) => path)).toEqual([
      '/settings/profile', '/settings/notifications', '/settings/privacy', '/settings/security',
    ])
    for (const gone of ['Cursos', 'Score en LinkedIn', 'Duolingo for Schools', 'Escoge un plan', 'Centro de ayuda']) {
      expect(screen.queryByText(gone)).toBeNull()
    }
  })
})
