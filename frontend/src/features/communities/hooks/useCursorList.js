import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * List with opaque cursors (`fetchPage(before)` -> { items, nextBefore }) and "cargar más" (T19b).
 * `getKey` dedupes (a row may appear in two pages if the list changes meanwhile).
 */
export function useCursorList(fetchPage, getKey = (item) => item.id) {
  const [items, setItems] = useState([])
  const [nextBefore, setNextBefore] = useState(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState(null)
  const requestRef = useRef(0)
  const getKeyRef = useRef(getKey)
  useEffect(() => { getKeyRef.current = getKey })

  const reload = useCallback(async () => {
    const id = ++requestRef.current
    setLoading(true)
    setError(null)
    try {
      const page = await fetchPage(null)
      if (id !== requestRef.current) return
      setItems(page.items)
      setNextBefore(page.nextBefore)
    } catch (err) {
      if (id !== requestRef.current) return
      setError(err)
      setItems([])
      setNextBefore(null)
    } finally {
      if (id === requestRef.current) setLoading(false)
    }
  }, [fetchPage])

  useEffect(() => {
    reload()
  }, [reload])

  const loadMore = useCallback(async () => {
    if (!nextBefore || loadingMore) return
    const id = requestRef.current
    setLoadingMore(true)
    try {
      const page = await fetchPage(nextBefore)
      if (id !== requestRef.current) return
      setItems((prev) => {
        const seen = new Set(prev.map((item) => getKeyRef.current(item)))
        return [...prev, ...page.items.filter((item) => !seen.has(getKeyRef.current(item)))]
      })
      setNextBefore(page.nextBefore)
    } catch (err) {
      if (id === requestRef.current) setError(err)
    } finally {
      setLoadingMore(false)
    }
  }, [fetchPage, nextBefore, loadingMore])

  const removeItem = useCallback((predicate) => setItems((prev) => prev.filter((item) => !predicate(item))), [])

  return { items, setItems, loading, loadingMore, error, hasMore: !!nextBefore, loadMore, reload, removeItem }
}
