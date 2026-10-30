import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VerifyEmail from './VerifyEmail.jsx'
import { verifyEmailRequest } from '../../features/auth/services/auth.service'

const setCurrentUser = vi.fn()

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ isAuthenticated: true, setCurrentUser }) }))
vi.mock('../../features/auth/services/auth.service', () => ({ verifyEmailRequest: vi.fn() }))

const open = (url) => render(<MemoryRouter initialEntries={[url]}><VerifyEmail /></MemoryRouter>)

describe('VerifyEmail', () => {
  beforeEach(() => {
    verifyEmailRequest.mockReset()
    setCurrentUser.mockReset()
  })

  it('consumes the token once and confirms', async () => {
    verifyEmailRequest.mockResolvedValue()
    open('/verify-email?token=abc')
    expect(await screen.findByText('Email verificado')).toBeInTheDocument()
    expect(verifyEmailRequest).toHaveBeenCalledWith('abc')
    expect(setCurrentUser).toHaveBeenCalled()
  })

  it('shows an inline error for an invalid or expired link', async () => {
    verifyEmailRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 400 }))
    open('/verify-email?token=old')
    expect(await screen.findByText(/no es válido o ha caducado/)).toBeInTheDocument()
  })

  it('does not call the server without a token', async () => {
    open('/verify-email')
    expect(await screen.findByText(/no es válido o ha caducado/)).toBeInTheDocument()
    await waitFor(() => expect(verifyEmailRequest).not.toHaveBeenCalled())
  })
})
