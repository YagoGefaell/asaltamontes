import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EmailVerificationRedirect from './EmailVerificationRedirect.jsx'
import { apiClient } from '../lib/api-client.js'

const navigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal()),
  useNavigate: () => navigate,
}))

describe('EmailVerificationRedirect', () => {
  afterEach(() => navigate.mockReset())

  it('sends her to the verification screen, remembering where she was', () => {
    render(<MemoryRouter initialEntries={['/posts/new']}><EmailVerificationRedirect /></MemoryRouter>)
    apiClient.emailNotVerifiedCallback()
    expect(navigate).toHaveBeenCalledWith('/verify-email/required', { state: { from: '/posts/new' } })
  })

  it('does not loop when she is already verifying', () => {
    render(<MemoryRouter initialEntries={['/verify-email/required']}><EmailVerificationRedirect /></MemoryRouter>)
    apiClient.emailNotVerifiedCallback()
    expect(navigate).not.toHaveBeenCalled()
  })
})
