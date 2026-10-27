import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}))

import { apiClient } from '../../../lib/api-client.js'
import {
  getNotificationPreferencesRequest,
  getNotificationsRequest,
  getUnreadCountRequest,
  markAllNotificationsReadRequest,
  markNotificationsReadRequest,
  updateNotificationPreferencesRequest,
} from './notifications.service.js'

describe('notifications service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('asks for a page with limit and the opaque cursor', async () => {
    apiClient.get.mockResolvedValue({ items: [{ id: 'a' }], hasMore: true, nextBefore: 'tok' })

    const page = await getNotificationsRequest({ before: 'abc' })

    expect(apiClient.get).toHaveBeenCalledWith('/api/notifications?limit=20&before=abc')
    expect(page).toEqual({ items: [{ id: 'a' }], hasMore: true, nextBefore: 'tok' })
  })

  it('omits the cursor on the first page and tolerates an empty answer', async () => {
    apiClient.get.mockResolvedValue(null)
    expect(await getNotificationsRequest()).toEqual({ items: [], hasMore: false, nextBefore: null })
    expect(apiClient.get).toHaveBeenCalledWith('/api/notifications?limit=20')
  })

  it('reads the counter', async () => {
    apiClient.get.mockResolvedValue({ count: 7 })
    expect(await getUnreadCountRequest()).toBe(7)
  })

  it('marks some or all as read', async () => {
    await markNotificationsReadRequest(['a', 'b'])
    expect(apiClient.post).toHaveBeenCalledWith('/api/notifications/read', { ids: ['a', 'b'] })
    await markAllNotificationsReadRequest()
    expect(apiClient.post).toHaveBeenCalledWith('/api/notifications/read', { all: true })
  })

  it('reads and patches the preferences', async () => {
    apiClient.get.mockResolvedValue({ types: { NEW_FOLLOWER: true } })
    expect(await getNotificationPreferencesRequest()).toEqual({ NEW_FOLLOWER: true })
    apiClient.patch.mockResolvedValue({ types: { NEW_FOLLOWER: false } })
    expect(await updateNotificationPreferencesRequest({ NEW_FOLLOWER: false })).toEqual({ NEW_FOLLOWER: false })
    expect(apiClient.patch).toHaveBeenCalledWith('/api/notifications/preferences', { types: { NEW_FOLLOWER: false } })
  })
})
