import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/events.service.js', () => ({ getEventsInBoundsRequest: vi.fn() }))

import { getEventsInBoundsRequest } from '../services/events.service.js'
import { BOUNDS_DEBOUNCE_MS, useEventsInBounds } from './useEventsInBounds.js'

const area = (n) => ({ minLat: n, maxLat: n + 1, minLng: n, maxLng: n + 1 })
const filters = { type: 'RACE' }

beforeEach(() => {
  vi.useFakeTimers()
  getEventsInBoundsRequest.mockReset()
})
afterEach(() => vi.useRealTimers())

const wait = (ms) => act(async () => { await vi.advanceTimersByTimeAsync(ms) })

describe('useEventsInBounds', () => {
  it('does not search until the map reports an area', async () => {
    renderHook(() => useEventsInBounds(null, filters))
    await wait(BOUNDS_DEBOUNCE_MS * 2)
    expect(getEventsInBoundsRequest).not.toHaveBeenCalled()
  })

  it('waits for the map to stop moving and searches once, with the filters', async () => {
    getEventsInBoundsRequest.mockResolvedValue({ items: [{ id: 1 }], total: 1 })
    const { result, rerender } = renderHook(({ b }) => useEventsInBounds(b, filters), { initialProps: { b: area(1) } })
    await wait(BOUNDS_DEBOUNCE_MS - 100)
    rerender({ b: area(2) })
    await wait(BOUNDS_DEBOUNCE_MS - 100)
    expect(getEventsInBoundsRequest).not.toHaveBeenCalled()
    await wait(200)
    expect(getEventsInBoundsRequest).toHaveBeenCalledTimes(1)
    expect(getEventsInBoundsRequest).toHaveBeenCalledWith(area(2), filters)
    expect(result.current.events).toEqual([{ id: 1 }])
    expect(result.current.total).toBe(1)
    expect(result.current.loading).toBe(false)
  })

  it('ignores the answer of a search that a newer one superseded', async () => {
    let resolveOld
    getEventsInBoundsRequest.mockImplementationOnce(() => new Promise((r) => { resolveOld = r }))
    getEventsInBoundsRequest.mockResolvedValueOnce({ items: [{ id: 2 }], total: 1 })
    const { result, rerender } = renderHook(({ b }) => useEventsInBounds(b, filters), { initialProps: { b: area(1) } })
    await wait(BOUNDS_DEBOUNCE_MS)
    rerender({ b: area(5) })
    await wait(BOUNDS_DEBOUNCE_MS)
    expect(result.current.events).toEqual([{ id: 2 }])
    await act(async () => resolveOld({ items: [{ id: 1 }], total: 1 }))
    expect(result.current.events).toEqual([{ id: 2 }])
  })

  it('keeps the previous pins while searching and reports errors, with a retry', async () => {
    getEventsInBoundsRequest.mockResolvedValueOnce({ items: [{ id: 1 }], total: 1 })
    const { result, rerender } = renderHook(({ b }) => useEventsInBounds(b, filters), { initialProps: { b: area(1) } })
    await wait(BOUNDS_DEBOUNCE_MS)
    getEventsInBoundsRequest.mockRejectedValueOnce(new Error('Fallo'))
    rerender({ b: area(3) })
    expect(result.current.events).toEqual([{ id: 1 }])
    expect(result.current.loading).toBe(true)
    await wait(BOUNDS_DEBOUNCE_MS)
    expect(result.current.error).toBe('Fallo')

    getEventsInBoundsRequest.mockResolvedValueOnce({ items: [{ id: 3 }], total: 1 })
    act(() => result.current.refetch())
    await wait(BOUNDS_DEBOUNCE_MS)
    expect(result.current.error).toBeNull()
    expect(result.current.events).toEqual([{ id: 3 }])
  })
})
