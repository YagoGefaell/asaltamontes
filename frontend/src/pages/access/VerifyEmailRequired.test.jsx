import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import VerifyEmailRequired from './VerifyEmailRequired.jsx'

const navigate = vi.fn()

vi.mock('../../features/auth/components/VerifyEmailPanel.jsx', () => ({ default: () => <p>panel</p> }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

describe('VerifyEmailRequired', () => {
  it('explains and goes back to where she came from', () => {
    render(<MemoryRouter initialEntries={[{ pathname: '/verify-email/required', state: { from: '/posts/new' } }]}><VerifyEmailRequired /></MemoryRouter>)
    expect(screen.getByText('Para hacer esto necesitas verificar tu email.')).toBeInTheDocument()
    fireEvent.click(screen.getByText('Volver'))
    expect(navigate).toHaveBeenCalledWith('/posts/new', { replace: true })
  })
})
