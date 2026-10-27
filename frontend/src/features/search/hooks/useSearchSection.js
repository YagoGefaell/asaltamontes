import { useCallback, useEffect, useRef, useState } from 'react'

const keyOf = (item) => item.id ?? item.username

/**
 * One paged list of the search (people, communities or events).
 * `fetchPage(page)` -> { items, hasMore }; it is called again when `searchKey` changes.
 * Disabled = no request and an empty list. Only the answer to the latest request counts.
 */
export function useSearchSection(fetchPage, searchKey, enabled = true) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState(null)
  const pageRef = useRef(0)
  const requestRef = useRef(0)
  const fetchRef = useRef(fetchPage)
  fetchRef.current = fetchPage

  const load = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setError(null)
    try {
      const result = await fetchRef.current(0)
      if (requestId !== requestRef.current) return
      pageRef.current = 0
      setItems(result.items)
      setHasMore(result.hasMore)
    } catch (err) {
      if (requestId !== requestRef.current) return
      setError(err.message || 'No se ha podido cargar')
      setItems([])
      setHasMore(false)
    } finally {
      if (requestId === requestRef.current) setLoading(false)
    }
  }, [])

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || loadingMore) return
    const requestId = requestRef.current
    setLoadingMore(true)
    try {
      const result = await fetchRef.current(pageRef.current + 1)
      if (requestId !== requestRef.current) return
      pageRef.current += 1
      setItems((prev) => {
        const seen = new Set(prev.map(keyOf))
        return [...prev, ...result.items.filter((item) => !seen.has(keyOf(item)))]
      })
      setHasMore(result.hasMore)
    } catch (err) {
      if (requestId === requestRef.current) {
        setError(err.message || 'No se ha podido cargar')
        setHasMore(false)
      }
    } finally {
      setLoadingMore(false)
    }
  }, [hasMore, loading, loadingMore])

  useEffect(() => {
    if (enabled) {
      load()
    } else {
      requestRef.current += 1
      setItems([])
      setError(null)
      setLoading(false)
      setHasMore(false)
    }
  }, [enabled, searchKey, load])

  return { items, loading, loadingMore, hasMore, error, loadMore, retry: load }
}
