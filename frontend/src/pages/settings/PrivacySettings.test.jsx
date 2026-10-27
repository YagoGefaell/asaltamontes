import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PrivacySettings from './PrivacySettings.jsx'
import { ToastProvider } from '../../shared/ui/Toast.jsx'
import { getBlockedUsersRequest, unblockUserRequest } from '../../features/users/services/users.service'

const updateUserProfile = vi.fn()

vi.mock('../../features/users/hooks/useUserProfile', () => ({
  useUserProfile: () => ({ userProfile: { dmPolicy: 'EVERYONE' }, updateUserProfile }),
}))
vi.mock('../../features/users/services/users.service', () => ({
  getBlockedUsersRequest: vi.fn(),
  unblockUserRequest: vi.fn(),
}))

function renderPage() {
  return render(<ToastProvider><PrivacySettings /></ToastProvider>)
}

describe('PrivacySettings', () => {
  beforeEach(() => {
    updateUserProfile.mockReset()
    getBlockedUsersRequest.mockReset()
    unblockUserRequest.mockReset()
  })

  it('saves the chosen dm policy', async () => {
    getBlockedUsersRequest.mockResolvedValue({ content: [], last: true })
    updateUserProfile.mockResolvedValue({ dmPolicy: 'NOBODY' })
    renderPage()

    fireEvent.change(screen.getByLabelText('Quién puede escribirme'), { target: { value: 'NOBODY' } })

    await waitFor(() => expect(updateUserProfile).toHaveBeenCalledWith({ dmPolicy: 'NOBODY' }))
    expect(await screen.findByText('No has bloqueado a nadie.')).toBeTruthy()
  })

  it('lists the blocked users and unblocks one after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    getBlockedUsersRequest.mockResolvedValue({
      content: [{ username: 'marta', fullName: 'Marta', profilePictureUrl: null }],
      last: true,
    })
    unblockUserRequest.mockResolvedValue()
    renderPage()

    expect(await screen.findByText('@marta')).toBeTruthy()
    fireEvent.click(screen.getByText('Desbloquear'))

    await waitFor(() => expect(unblockUserRequest).toHaveBeenCalledWith('marta'))
    await waitFor(() => expect(screen.queryByText('@marta')).toBeNull())
  })
})
