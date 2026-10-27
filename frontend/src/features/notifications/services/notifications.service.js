import { apiClient } from '../../../lib/api-client.js'

export const NOTIFICATIONS_PAGE_SIZE = 20

/** One page, newest first. `before` is the opaque nextBefore of the previous page (T18). */
export async function getNotificationsRequest({ before, limit = NOTIFICATIONS_PAGE_SIZE } = {}) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (before) params.set('before', before)
  const page = await apiClient.get(`/api/notifications?${params}`)
  return {
    items: Array.isArray(page?.items) ? page.items : [],
    hasMore: page?.hasMore === true,
    nextBefore: page?.nextBefore ?? null,
  }
}

export async function getUnreadCountRequest() {
  const result = await apiClient.get('/api/notifications/unread-count')
  return Number.isFinite(result?.count) ? result.count : 0
}

/** At most 50 ids per request (the API rejects more). */
export function markNotificationsReadRequest(ids) {
  return apiClient.post('/api/notifications/read', { ids })
}

export function markAllNotificationsReadRequest() {
  return apiClient.post('/api/notifications/read', { all: true })
}

/** { NEW_FOLLOWER: true, ... }: every type, enabled unless switched off */
export async function getNotificationPreferencesRequest() {
  const result = await apiClient.get('/api/notifications/preferences')
  return result?.types ?? {}
}

/** Only the types given change; resolves with all of them. */
export async function updateNotificationPreferencesRequest(types) {
  const result = await apiClient.patch('/api/notifications/preferences', { types })
  return result?.types ?? {}
}
