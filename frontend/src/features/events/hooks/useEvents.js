import { useState, useCallback, useEffect, useRef } from 'react'
import { getEventsRequest } from '../services/events.service.js'

/** Events by pages for some filters (infinite scroll: the page asks for loadMore). `filters` must be memoized. */
export function useEvents(filters, enabled = true) {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState(null)
  const pageRef = useRef(0)
  // Only the answer to the latest search counts
  const requestRef = useRef(0)

  const fetchFirst = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setError(null)
    try {
      const page = await getEventsRequest({ ...filters, page: 0 })
      if (requestId !== requestRef.current) return
      pageRef.current = page.page
      setEvents(page.items)
      setHasMore(page.hasMore)
    } catch (err) {
      if (requestId !== requestRef.current) return
      setError(err.message)
      setEvents([])
      setHasMore(false)
    } finally {
      if (requestId === requestRef.current) setLoading(false)
    }
  }, [filters])

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || loadingMore) return
    const requestId = requestRef.current
    setLoadingMore(true)
    try {
      const page = await getEventsRequest({ ...filters, page: pageRef.current + 1 })
      if (requestId !== requestRef.current) return
      pageRef.current = page.page
      // Events can move between pages while she scrolls: one card per id
      setEvents((prev) => {
        const seen = new Set(prev.map((e) => e.id))
        return [...prev, ...page.items.filter((e) => !seen.has(e.id))]
      })
      setHasMore(page.hasMore)
    } catch (err) {
      if (requestId === requestRef.current) {
        setError(err.message)
        setHasMore(false)
      }
    } finally {
      setLoadingMore(false)
    }
  }, [filters, hasMore, loading, loadingMore])

  // Disabled (e.g. the map view is showing): no request until she comes back; it then catches up with the filters
  useEffect(() => {
    if (enabled) fetchFirst()
  }, [fetchFirst, enabled])

  return { events, setEvents, loading, loadingMore, hasMore, loadMore, error, refetch: fetchFirst }
}
