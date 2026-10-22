import { act, renderHook, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../services/events.service.js', () => ({
  joinEventRequest: vi.fn(),
  leaveEventRequest: vi.fn(),
}))

import { joinEventRequest, leaveEventRequest } from '../services/events.service.js'
import { optimisticEvent, useEventAttendance } from './useEventAttendance.js'

const base = { id: 5, capacity: 3, attendeeCount: 1, myStatus: null }

function setup(initial, onError) {
  return renderHook(() => {
    const [event, setEvent] = useState(initial)
    return { event, ...useEventAttendance(event, setEvent, { onError }) }
  })
}

describe('optimisticEvent', () => {
  it('joining a free event adds her to GOING', () => {
    expect(optimisticEvent(base, 'join')).toMatchObject({ myStatus: 'GOING', attendeeCount: 2 })
  })

  it('joining a full event goes to the waitlist without taking a place', () => {
    const full = { ...base, attendeeCount: 3 }
    expect(optimisticEvent(full, 'join')).toMatchObject({ myStatus: 'WAITLIST', attendeeCount: 3 })
  })

  it('leaving frees a place only if she was going', () => {
    expect(optimisticEvent({ ...base, myStatus: 'GOING' }, 'leave')).toMatchObject({ myStatus: 'CANCELLED', attendeeCount: 0 })
    expect(optimisticEvent({ ...base, attendeeCount: 3, myStatus: 'WAITLIST' }, 'leave')).toMatchObject({ myStatus: 'CANCELLED', attendeeCount: 3 })
  })
})

describe('useEventAttendance', () => {
  it('shows the change at once and then takes the server answer', async () => {
    let resolve
    joinEventRequest.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result } = setup(base)

    act(() => { result.current.join() })
    expect(result.current.event.myStatus).toBe('GOING')
    expect(result.current.pending).toBe(true)

    // the server decided she is on the waitlist (someone else took the last place)
    await act(async () => { resolve({ ...base, attendeeCount: 3, myStatus: 'WAITLIST' }) })
    expect(result.current.event).toMatchObject({ myStatus: 'WAITLIST', attendeeCount: 3 })
    expect(result.current.pending).toBe(false)
  })

  it('rolls back and reports the error when the request fails', async () => {
    joinEventRequest.mockRejectedValue(new Error('Quedada cancelada'))
    const onError = vi.fn()
    const { result } = setup(base, onError)

    await act(async () => { await result.current.join() })
    expect(result.current.event).toEqual(base)
    expect(onError).toHaveBeenCalledWith('Quedada cancelada')
    await waitFor(() => expect(result.current.pending).toBe(false))
  })

  it('ignores a second tap while the first is in flight', async () => {
    let resolve
    leaveEventRequest.mockReturnValue(new Promise((r) => { resolve = r }))
    const { result } = setup({ ...base, myStatus: 'GOING' })

    act(() => { result.current.leave() })
    act(() => { result.current.leave() })
    expect(leaveEventRequest).toHaveBeenCalledTimes(1)
    await act(async () => { resolve({ ...base, attendeeCount: 0, myStatus: 'CANCELLED' }) })
  })
})
