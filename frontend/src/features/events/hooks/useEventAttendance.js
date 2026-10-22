import { useState, useRef, useCallback } from 'react'
import { joinEventRequest, leaveEventRequest } from '../services/events.service.js'
import { isFull } from '../utils/eventFormat.js'

/** What the event looks like if the join/leave works (the server confirms it afterwards). */
export function optimisticEvent(event, action) {
  if (action === 'join') {
    const status = isFull(event) ? 'WAITLIST' : 'GOING'
    return { ...event, myStatus: status, attendeeCount: event.attendeeCount + (status === 'GOING' ? 1 : 0) }
  }
  const wasGoing = event.myStatus === 'GOING'
  return { ...event, myStatus: 'CANCELLED', attendeeCount: Math.max(0, event.attendeeCount - (wasGoing ? 1 : 0)) }
}

/**
 * Join/leave with optimistic update and rollback. `event` and `onChange(event)` belong to the owner of the
 * event (the detail page); onChange gets the optimistic event, then the server's, or the previous one on error.
 * useOptimisticToggle does not fit: there are three outcomes (GOING / WAITLIST / out) and the server decides.
 */
export function useEventAttendance(event, onChange, { onError } = {}) {
  const [pending, setPending] = useState(false)
  const busyRef = useRef(false)

  const run = useCallback(
    async (action) => {
      if (busyRef.current || !event) return
      busyRef.current = true
      setPending(true)
      const previous = event
      onChange(optimisticEvent(previous, action))
      try {
        const confirmed = await (action === 'join' ? joinEventRequest(previous.id) : leaveEventRequest(previous.id))
        onChange(confirmed)
      } catch (err) {
        onChange(previous)
        onError?.(err.message)
      } finally {
        busyRef.current = false
        setPending(false)
      }
    },
    [event, onChange, onError],
  )

  const join = useCallback(() => run('join'), [run])
  const leave = useCallback(() => run('leave'), [run])

  return { pending, join, leave }
}
