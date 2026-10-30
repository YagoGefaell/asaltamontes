import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../lib/api-client.js'
import {
  dismissReportRequest,
  getAdminReportsRequest,
  removeReportedContentRequest,
} from './admin.service.js'

vi.mock('../../../lib/api-client.js', () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

describe('admin service', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sends the filters and the cursor, and normalises the page', async () => {
    apiClient.get.mockResolvedValue({ items: [{ id: 'r1' }], hasMore: true, nextBefore: 'c' })
    const page = await getAdminReportsRequest({ status: 'OPEN', type: 'POST', before: 'x' })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/reports?limit=20&status=OPEN&type=POST&before=x')
    expect(page).toEqual({ items: [{ id: 'r1' }], hasMore: true, nextBefore: 'c' })
  })

  it('posts the actions with an optional note', async () => {
    await dismissReportRequest('r1', '')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/reports/r1/dismiss', { resolution: null })
    await removeReportedContentRequest('r1', 'spam')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/reports/r1/remove-content', { resolution: 'spam' })
  })
})
