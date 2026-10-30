import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ChangeEmail from './ChangeEmail.jsx'
import { requestEmailChangeRequest } from '../../features/auth/services/auth.service'

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ currentUser: { email: 'old@example.com' } }) }))
vi.mock('../../features/auth/services/auth.service', () => ({ requestEmailChangeRequest: vi.fn() }))

const fail = (status) => Object.assign(new Error('x'), { status })
const fill = () => {
  fireEvent.change(screen.getByLabelText('Email nuevo'), { target: { value: 'New@Example.com' } })
  fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'secret123' } })
  fireEvent.click(screen.getByText('Enviar enlace'))
}

describe('ChangeEmail', () => {
  beforeEach(() => { requestEmailChangeRequest.mockReset() })

  it('sends the request and says the link went to the new address', async () => {
    requestEmailChangeRequest.mockResolvedValue()
    render(<ChangeEmail />)
    fill()
    expect(await screen.findByText(/Te hemos enviado un enlace a new@example.com/)).toBeInTheDocument()
    expect(requestEmailChangeRequest).toHaveBeenCalledWith({ newEmail: 'new@example.com', currentPassword: 'secret123' })
  })

  it('marks the password field on 403', async () => {
    requestEmailChangeRequest.mockRejectedValue(fail(403))
    render(<ChangeEmail />)
    fill()
    expect(await screen.findByText('La contraseña no es correcta')).toBeInTheDocument()
  })

  it('shows 409 and 429 above the button', async () => {
    requestEmailChangeRequest.mockRejectedValueOnce(fail(409))
    render(<ChangeEmail />)
    fill()
    expect(await screen.findByText(/No se puede usar ese email/)).toBeInTheDocument()
    requestEmailChangeRequest.mockRejectedValueOnce(Object.assign(fail(429), { retryAfter: 600 }))
    fireEvent.click(screen.getByText('Enviar enlace'))
    expect(await screen.findByText(/Inténtalo de nuevo en 10 min/)).toBeInTheDocument()
  })

  it('does not call the server without a password', () => {
    render(<ChangeEmail />)
    fireEvent.change(screen.getByLabelText('Email nuevo'), { target: { value: 'new@example.com' } })
    fireEvent.click(screen.getByText('Enviar enlace'))
    expect(screen.getByText('Escribe tu contraseña actual')).toBeInTheDocument()
    expect(requestEmailChangeRequest).not.toHaveBeenCalled()
  })
})
