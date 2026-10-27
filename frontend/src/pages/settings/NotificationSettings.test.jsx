import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationSettings from './NotificationSettings.jsx'
import { ToastProvider } from '../../shared/ui/Toast.jsx'
import {
  getNotificationPreferencesRequest,
  updateNotificationPreferencesRequest,
} from '../../features/notifications/services/notifications.service'

vi.mock('../../features/notifications/services/notifications.service', () => ({
  getNotificationPreferencesRequest: vi.fn(),
  updateNotificationPreferencesRequest: vi.fn(),
}))

const renderPage = () => render(<ToastProvider><NotificationSettings /></ToastProvider>)

describe('NotificationSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getNotificationPreferencesRequest.mockResolvedValue({ NEW_FOLLOWER: true, EVENT_JOINED: false })
  })

  it('shows a switch per type with its current value', async () => {
    renderPage()

    const follower = await screen.findByLabelText('Alguien empieza a seguirme')
    expect(follower.checked).toBe(true)
    expect(screen.getByLabelText('Alguien se apunta a una quedada mía').checked).toBe(false)
  })

  it('saves a change and flips at once', async () => {
    updateNotificationPreferencesRequest.mockResolvedValue({})
    renderPage()
    const follower = await screen.findByLabelText('Alguien empieza a seguirme')

    fireEvent.click(follower)

    expect(follower.checked).toBe(false)
    await waitFor(() => expect(updateNotificationPreferencesRequest).toHaveBeenCalledWith({ NEW_FOLLOWER: false }))
  })

  it('rolls the switch back when saving fails', async () => {
    updateNotificationPreferencesRequest.mockRejectedValue(new Error('No se pudo'))
    renderPage()
    const follower = await screen.findByLabelText('Alguien empieza a seguirme')

    fireEvent.click(follower)

    await waitFor(() => expect(follower.checked).toBe(true))
    expect(await screen.findByText('No se pudo')).toBeTruthy()
  })
})
