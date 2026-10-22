import { useState, useEffect, useCallback } from 'react'
import { getEventRequest } from '../services/events.service.js'

/** One event by id; `setEvent` lets the attendance button apply optimistic changes. */
export function useEvent(eventId) {
  const [event, setEvent] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setEvent(await getEventRequest(eventId))
    } catch (err) {
      setEvent(null)
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [eventId])

  useEffect(() => {
    load()
  }, [load])

  return { event, setEvent, loading, error, refetch: load }
}
