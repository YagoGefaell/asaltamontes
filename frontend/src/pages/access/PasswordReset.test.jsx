import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ForgotPassword from './ForgotPassword.jsx'
import ResetPassword from './ResetPassword.jsx'
import { forgotPasswordRequest, resetPasswordRequest } from '../../features/auth/services/auth.service'

vi.mock('../../features/auth/services/auth.service', () => ({
  forgotPasswordRequest: vi.fn(),
  resetPasswordRequest: vi.fn(),
}))

const fail = (status, extra = {}) => Object.assign(new Error('x'), { status, ...extra })
const type = (label, value) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

describe('ForgotPassword', () => {
  beforeEach(() => { forgotPasswordRequest.mockReset() })
  const open = () => render(<MemoryRouter><ForgotPassword /></MemoryRouter>)

  it('shows the same neutral confirmation whatever the address', async () => {
    forgotPasswordRequest.mockResolvedValue()
    open()
    type('Email', 'Ana@Example.com')
    fireEvent.click(screen.getByText('Enviar enlace'))
    expect(await screen.findByText(/Si hay una cuenta con ese email/)).toBeInTheDocument()
    expect(forgotPasswordRequest).toHaveBeenCalledWith('ana@example.com')
  })

  it('rejects a malformed address without calling the server', () => {
    open()
    type('Email', 'nope')
    fireEvent.click(screen.getByText('Enviar enlace'))
    expect(screen.getByText('Formato de email inválido')).toBeInTheDocument()
    expect(forgotPasswordRequest).not.toHaveBeenCalled()
  })

  it('shows the rate limit inline', async () => {
    forgotPasswordRequest.mockRejectedValue(fail(429, { retryAfter: 1800 }))
    open()
    type('Email', 'ana@example.com')
    fireEvent.click(screen.getByText('Enviar enlace'))
    expect(await screen.findByText(/Inténtalo de nuevo en 30 min/)).toBeInTheDocument()
  })
})

describe('ResetPassword', () => {
  beforeEach(() => { resetPasswordRequest.mockReset() })
  const open = (url = '/reset-password?token=abc') =>
    render(<MemoryRouter initialEntries={[url]}><ResetPassword /></MemoryRouter>)

  it('sends the token and the new password, then offers to log in', async () => {
    resetPasswordRequest.mockResolvedValue()
    open()
    type('Contraseña nueva', 'password123')
    type('Repite la contraseña', 'password123')
    fireEvent.click(screen.getByText('Cambiar contraseña'))
    expect(await screen.findByText('Contraseña cambiada')).toBeInTheDocument()
    expect(resetPasswordRequest).toHaveBeenCalledWith({ token: 'abc', password: 'password123', confirmPassword: 'password123' })
  })

  it('does not call the server when the passwords differ', () => {
    open()
    type('Contraseña nueva', 'password123')
    type('Repite la contraseña', 'password124')
    fireEvent.click(screen.getByText('Cambiar contraseña'))
    expect(screen.getByText('Las contraseñas no coinciden')).toBeInTheDocument()
    expect(resetPasswordRequest).not.toHaveBeenCalled()
  })

  it('shows the invalid-link screen on 400', async () => {
    resetPasswordRequest.mockRejectedValue(fail(400))
    open()
    type('Contraseña nueva', 'password123')
    type('Repite la contraseña', 'password123')
    fireEvent.click(screen.getByText('Cambiar contraseña'))
    expect(await screen.findByText(/no es válido o ha caducado/)).toBeInTheDocument()
  })

  it('shows the invalid-link screen without a token', async () => {
    open('/reset-password')
    expect(screen.getByText(/no es válido o ha caducado/)).toBeInTheDocument()
    await waitFor(() => expect(resetPasswordRequest).not.toHaveBeenCalled())
  })
})
