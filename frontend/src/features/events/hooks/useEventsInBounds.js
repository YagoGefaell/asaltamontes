import { useCallback, useEffect, useState } from 'react'
import { getEventsInBoundsRequest } from '../services/events.service.js'

export const BOUNDS_DEBOUNCE_MS = 400

/**
 * Events of the visible map area. The search waits for the map to stop moving (debounce) and only the answer to
 * the latest one counts; the previous pins stay on screen meanwhile. `filters` must be memoized; no `bounds` yet = no request.
 */
export function useEventsInBounds(bounds, filters, debounceMs = BOUNDS_DEBOUNCE_MS) {
  const [events, setEvents] = useState([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!bounds) return undefined
    // Set by the cleanup: a newer search (or unmount) supersedes this one, in flight or not
    let superseded = false
    setLoading(true)
    const timeoutId = setTimeout(async () => {
      try {
        const result = await getEventsInBoundsRequest(bounds, filters)
        if (superseded) return
        setEvents(result.items)
        setTotal(result.total)
        setError(null)
      } catch (err) {
        if (!superseded) setError(err.message)
      } finally {
        if (!superseded) setLoading(false)
      }
    }, debounceMs)
    return () => {
      clearTimeout(timeoutId)
      superseded = true
    }
  }, [bounds, filters, debounceMs, attempt])

  const refetch = useCallback(() => setAttempt((n) => n + 1), [])

  return { events, total, loading, error, refetch }
}
