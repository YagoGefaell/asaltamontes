import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VerifyEmailPanel from './VerifyEmailPanel.jsx'
import { resendVerificationEmailRequest } from '../services/auth.service'

let currentUser
const setCurrentUser = vi.fn()

vi.mock('../hooks/useAuth', () => ({ useAuth: () => ({ currentUser, setCurrentUser }) }))
vi.mock('../services/auth.service', () => ({ resendVerificationEmailRequest: vi.fn() }))

describe('VerifyEmailPanel', () => {
  beforeEach(() => {
    currentUser = { email: 'ana@example.com', isVerified: false }
    setCurrentUser.mockReset()
    resendVerificationEmailRequest.mockReset()
  })

  it('says the email is verified, without a button', () => {
    currentUser = { email: 'ana@example.com', isVerified: true }
    render(<VerifyEmailPanel />)
    expect(screen.getByText(/está verificado/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('resends and starts the cooldown', async () => {
    resendVerificationEmailRequest.mockResolvedValue()
    render(<VerifyEmailPanel />)
    fireEvent.click(screen.getByText('Reenviar enlace'))
    expect(await screen.findByText('Reenviar (60 s)')).toBeDisabled()
    expect(screen.getByText(/Te hemos enviado el enlace a ana@example.com/)).toBeInTheDocument()
  })

  it('shows a 429 inline', async () => {
    resendVerificationEmailRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 429, retryAfter: 20 }))
    render(<VerifyEmailPanel />)
    fireEvent.click(screen.getByText('Reenviar enlace'))
    expect(await screen.findByText('Espera un momento antes de pedir otro enlace.')).toBeInTheDocument()
    expect(screen.getByText('Reenviar (20 s)')).toBeDisabled()
  })

  it('marks her as verified when the server says it already is', async () => {
    resendVerificationEmailRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 409 }))
    render(<VerifyEmailPanel />)
    fireEvent.click(screen.getByText('Reenviar enlace'))
    await vi.waitFor(() => expect(setCurrentUser).toHaveBeenCalled())
  })
})
