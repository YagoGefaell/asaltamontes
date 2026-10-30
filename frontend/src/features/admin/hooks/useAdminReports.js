import { useCallback, useEffect, useRef, useState } from 'react'
import { getAdminReportsRequest } from '../services/admin.service.js'

const LOAD_ERROR = 'No se han podido cargar las denuncias.'

/** The moderation queue for a status/type filter, with "load more". Changing a filter starts again from the first page. */
export function useAdminReports({ status, type }) {
  const [items, setItems] = useState([])
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const cursor = useRef(null)
  const generation = useRef(0)

  const load = useCallback(async (append) => {
    const mine = ++generation.current
    setLoading(true)
    setError('')
    try {
      const page = await getAdminReportsRequest({ status, type, before: append ? cursor.current : undefined })
      if (mine !== generation.current) return
      cursor.current = page.nextBefore
      setHasMore(page.hasMore)
      setItems((current) => (append ? [...current, ...page.items] : page.items))
    } catch {
      if (mine === generation.current) setError(LOAD_ERROR)
    } finally {
      if (mine === generation.current) setLoading(false)
    }
  }, [status, type])

  useEffect(() => {
    cursor.current = null
    load(false)
  }, [load])

  return {
    items,
    hasMore,
    loading,
    error,
    loadMore: () => load(true),
    retry: () => load(false),
  }
}
