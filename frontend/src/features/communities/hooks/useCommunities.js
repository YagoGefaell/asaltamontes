import { useState, useCallback, useEffect } from 'react'
import { getCommunitiesRequest } from '../services/communities.service.js'

export function useCommunities() {
  const [communities, setCommunities] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const fetchCommunities = useCallback(async () => {
    setLoading(true)
    setError(null)
    
    try {
      const data = await getCommunitiesRequest()
      setCommunities(data)
    } catch (err) {
      setError(err.message)
      setCommunities([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchCommunities()
  }, [fetchCommunities])

  return {
    communities,
    setCommunities,
    loading,
    error,
    refetch: fetchCommunities,
  }
}
