import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ConfirmEmailChange from './ConfirmEmailChange.jsx'
import { confirmEmailChangeRequest } from '../../features/auth/services/auth.service'

vi.mock('../../features/auth/hooks/useAuth', () => ({ useAuth: () => ({ isAuthenticated: true }) }))
vi.mock('../../features/auth/services/auth.service', () => ({ confirmEmailChangeRequest: vi.fn() }))

const open = (url) => render(<MemoryRouter initialEntries={[url]}><ConfirmEmailChange /></MemoryRouter>)

describe('ConfirmEmailChange', () => {
  beforeEach(() => { confirmEmailChangeRequest.mockReset() })

  it('consumes the token once and confirms', async () => {
    confirmEmailChangeRequest.mockResolvedValue()
    open('/confirm-email-change?token=abc')
    expect(await screen.findByText('Email cambiado')).toBeInTheDocument()
    expect(confirmEmailChangeRequest).toHaveBeenCalledWith('abc')
  })

  it('shows an inline error for an invalid link', async () => {
    confirmEmailChangeRequest.mockRejectedValue(Object.assign(new Error('x'), { status: 400 }))
    open('/confirm-email-change?token=old')
    expect(await screen.findByText(/no es válido o ha caducado/)).toBeInTheDocument()
  })

  it('does not call the server without a token', async () => {
    open('/confirm-email-change')
    expect(screen.getByText(/no es válido o ha caducado/)).toBeInTheDocument()
    await waitFor(() => expect(confirmEmailChangeRequest).not.toHaveBeenCalled())
  })
})
