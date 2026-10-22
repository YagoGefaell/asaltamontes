import { describe, expect, it, vi } from 'vitest'

vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

import { apiClient } from '../../../lib/api-client.js'
import {
  buildEventsQuery, getEventAttendeesRequest, getEventsRequest, joinEventRequest, leaveEventRequest,
} from './events.service.js'

describe('buildEventsQuery', () => {
  it('sends only the filters that have a value', () => {
    const params = buildEventsQuery({ page: 2, type: 'RACE', level: '', city: '  Madrid ', mine: true })
    expect(params.get('page')).toBe('2')
    expect(params.get('type')).toBe('RACE')
    expect(params.get('city')).toBe('Madrid')
    expect(params.get('attending')).toBe('me')
    expect(params.has('level')).toBe(false)
    expect(params.has('from')).toBe(false)
  })

  it('does not send attending when "mis quedadas" is off', () => {
    expect(buildEventsQuery({ mine: false }).has('attending')).toBe(false)
  })
})

describe('events service', () => {
  it('maps a Spring page and its hasMore flag', async () => {
    apiClient.get.mockResolvedValue({ content: [{ id: 1 }], last: false, number: 0 })
    const page = await getEventsRequest({ from: '2026-10-25T00:00:00Z' })
    expect(apiClient.get.mock.calls[0][0]).toMatch(/^\/api\/events\?.*from=2026-10-25/)
    expect(page).toEqual({ items: [{ id: 1 }], hasMore: true, page: 0 })
  })

  it('tolerates an empty answer', async () => {
    apiClient.get.mockResolvedValue(null)
    expect(await getEventsRequest()).toEqual({ items: [], hasMore: false, page: 0 })
  })

  it('wraps errors keeping the server message', async () => {
    apiClient.get.mockRejectedValue(new Error('Boom'))
    await expect(getEventsRequest()).rejects.toThrow('Boom')
  })

  it('joins and leaves with POST', async () => {
    apiClient.post.mockResolvedValue({ id: 7, myStatus: 'GOING' })
    expect(await joinEventRequest(7)).toEqual({ id: 7, myStatus: 'GOING' })
    expect(apiClient.post).toHaveBeenCalledWith('/api/events/7/join', {})
    await leaveEventRequest(7)
    expect(apiClient.post).toHaveBeenCalledWith('/api/events/7/leave', {})
  })

  it('asks for the attendees of a status', async () => {
    apiClient.get.mockResolvedValue({ content: [], last: true, number: 0 })
    await getEventAttendeesRequest(7, { status: 'WAITLIST' })
    expect(apiClient.get.mock.calls[0][0]).toMatch(/^\/api\/events\/7\/attendees\?.*status=WAITLIST/)
  })
})
