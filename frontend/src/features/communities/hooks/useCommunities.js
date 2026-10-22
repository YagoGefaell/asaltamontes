import { useState, useCallback, useEffect, useRef } from 'react'
import { getCommunitiesRequest } from '../services/communities.service.js'

/** Communities by pages, optionally filtered by name (T14: "cargar más" and search). */
export function useCommunities(query = '') {
  const [communities, setCommunities] = useState([])
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState(null)
  const pageRef = useRef(0)
  // Only the answer to the latest search counts (typing fast fires several)
  const requestRef = useRef(0)

  const fetchCommunities = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setError(null)

    try {
      const page = await getCommunitiesRequest({ page: 0, query })
      if (requestId !== requestRef.current) return
      pageRef.current = page.page
      setCommunities(page.items)
      setHasMore(page.hasMore)
    } catch (err) {
      if (requestId !== requestRef.current) return
      setError(err.message)
      setCommunities([])
      setHasMore(false)
    } finally {
      if (requestId === requestRef.current) setLoading(false)
    }
  }, [query])

  const loadMore = useCallback(async () => {
    if (!hasMore || loadingMore) return
    const requestId = requestRef.current
    setLoadingMore(true)
    try {
      const page = await getCommunitiesRequest({ page: pageRef.current + 1, query })
      if (requestId !== requestRef.current) return
      pageRef.current = page.page
      // A community may move between pages (members join meanwhile): one card per id
      setCommunities((prev) => {
        const seen = new Set(prev.map((c) => c.id))
        return [...prev, ...page.items.filter((c) => !seen.has(c.id))]
      })
      setHasMore(page.hasMore)
    } catch (err) {
      if (requestId === requestRef.current) setError(err.message)
    } finally {
      setLoadingMore(false)
    }
  }, [hasMore, loadingMore, query])

  useEffect(() => {
    fetchCommunities()
  }, [fetchCommunities])

  return {
    communities,
    setCommunities,
    loading,
    loadingMore,
    hasMore,
    loadMore,
    error,
    refetch: fetchCommunities,
  }
}
