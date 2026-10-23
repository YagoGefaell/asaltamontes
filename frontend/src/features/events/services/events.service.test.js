import { describe, expect, it, vi } from 'vitest'

vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

import { apiClient } from '../../../lib/api-client.js'
import {
  buildEventsQuery, getEventAttendeesRequest, getEventsInBoundsRequest, getEventsRequest, toBounds, joinEventRequest, leaveEventRequest,
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

describe('map search by bounding box', () => {
  const bounds = { minLat: 40, maxLat: 41, minLng: -4, maxLng: -3 }

  it('adds the four corners to the query only when given', () => {
    const params = buildEventsQuery({ bounds })
    expect(params.get('minLat')).toBe('40')
    expect(params.get('maxLat')).toBe('41')
    expect(params.get('minLng')).toBe('-4')
    expect(params.get('maxLng')).toBe('-3')
    expect(buildEventsQuery({}).has('minLat')).toBe(false)
  })

  it('clamps a zoomed-out map to the world, which is all the API accepts', () => {
    expect(toBounds({ south: -95, west: -200, north: 91, east: 230.123456789 })).toEqual({
      minLat: -90, maxLat: 90, minLng: -180, maxLng: 180,
    })
    expect(toBounds({ south: 40.123456789, west: -3.987654321, north: 41, east: -3 }).minLat).toBe(40.12346)
  })

  it('asks for the capped first page with the list filters and returns the total of the area', async () => {
    apiClient.get.mockResolvedValue({ content: [{ id: 1 }], totalElements: 77 })
    const result = await getEventsInBoundsRequest(bounds, { type: 'RACE', mine: true })
    const url = apiClient.get.mock.calls.at(-1)[0]
    expect(url).toMatch(/^\/api\/events\?/)
    const params = new URLSearchParams(url.split('?')[1])
    expect(params.get('size')).toBe('50')
    expect(params.get('page')).toBe('0')
    expect(params.get('type')).toBe('RACE')
    expect(params.get('attending')).toBe('me')
    expect(params.get('minLng')).toBe('-4')
    expect(result).toEqual({ items: [{ id: 1 }], total: 77 })
  })

  it('falls back to the number of items when the answer has no total', async () => {
    apiClient.get.mockResolvedValue({ content: [{ id: 1 }, { id: 2 }] })
    expect((await getEventsInBoundsRequest(bounds)).total).toBe(2)
  })
})
