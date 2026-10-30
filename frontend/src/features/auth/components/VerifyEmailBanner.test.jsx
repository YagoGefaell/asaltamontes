import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VerifyEmailBanner from './VerifyEmailBanner.jsx'
import { resendVerificationEmailRequest } from '../services/auth.service'

let currentUser
const setCurrentUser = vi.fn()

vi.mock('../hooks/useAuth', () => ({ useAuth: () => ({ currentUser, setCurrentUser }) }))
vi.mock('../services/auth.service', () => ({ resendVerificationEmailRequest: vi.fn() }))

describe('VerifyEmailBanner', () => {
  beforeEach(() => {
    currentUser = { email: 'ana@example.com', isVerified: false }
    setCurrentUser.mockReset()
    resendVerificationEmailRequest.mockReset()
  })

  it('is hidden when verified or while the profile has not loaded', () => {
    currentUser = { email: 'a@b.c', isVerified: true }
    const { container, rerender } = render(<VerifyEmailBanner />)
    expect(container).toBeEmptyDOMElement()
    currentUser = null
    rerender(<VerifyEmailBanner />)
    expect(container).toBeEmptyDOMElement()
  })

  it('resends and starts the cooldown', async () => {
    resendVerificationEmailRequest.mockResolvedValue()
    render(<VerifyEmailBanner />)
    fireEvent.click(screen.getByText('Reenviar'))
    expect(await screen.findByText('Reenviar (60 s)')).toBeDisabled()
    expect(screen.getByText(/ana@example.com/)).toBeInTheDocument()
  })

  it('shows a 429 inline', async () => {
    resendVerificationEmailRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 429, retryAfter: 20 }))
    render(<VerifyEmailBanner />)
    fireEvent.click(screen.getByText('Reenviar'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Espera un momento')
    expect(screen.getByText('Reenviar (20 s)')).toBeDisabled()
  })

  it('marks her as verified when the server says it already is', async () => {
    resendVerificationEmailRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 409 }))
    render(<VerifyEmailBanner />)
    fireEvent.click(screen.getByText('Reenviar'))
    await vi.waitFor(() => expect(setCurrentUser).toHaveBeenCalled())
  })
})
