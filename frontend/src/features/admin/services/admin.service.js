import { apiClient } from '../../../lib/api-client.js'

export const ADMIN_REPORTS_PAGE_SIZE = 20

/** One page of the moderation queue (T23c1). `before` is the opaque nextBefore of the previous page. */
export async function getAdminReportsRequest({ status, type, before, limit = ADMIN_REPORTS_PAGE_SIZE } = {}) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (status) params.set('status', status)
  if (type) params.set('type', type)
  if (before) params.set('before', before)
  const page = await apiClient.get(`/api/admin/reports?${params}`)
  return {
    items: Array.isArray(page?.items) ? page.items : [],
    hasMore: page?.hasMore === true,
    nextBefore: page?.nextBefore ?? null,
  }
}

export function getAdminReportRequest(id) {
  return apiClient.get(`/api/admin/reports/${encodeURIComponent(id)}`)
}

/** Closes only this report. The note is for the record: it is never shown to the reporter or the reported. */
export function dismissReportRequest(id, resolution) {
  return apiClient.post(`/api/admin/reports/${encodeURIComponent(id)}/dismiss`, { resolution: resolution || null })
}

/** Removes the reported content and closes every open report on it. */
export function removeReportedContentRequest(id, resolution) {
  return apiClient.post(`/api/admin/reports/${encodeURIComponent(id)}/remove-content`, { resolution: resolution || null })
}
