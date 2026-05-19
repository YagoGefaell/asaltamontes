import { useState, useEffect, useCallback } from 'react'
import { getCommunityByNameRequest } from '../services/communities.service'

export function useCommunityDetail(communityName) {
  const [community, setCommunity] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchCommunityDetail = useCallback(async () => {
    if (!communityName) {
      setError('Nombre de comunidad inválido')
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    try {
      const communityData = await getCommunityByNameRequest(communityName)
      setCommunity(communityData)
    } catch (err) {
      setError(err.message || 'Error cargando comunidad')
      setCommunity(null)
    } finally {
      setLoading(false)
    }
  }, [communityName])

  useEffect(() => {
    fetchCommunityDetail()
  }, [fetchCommunityDetail])

  const refetch = useCallback(() => {
    fetchCommunityDetail()
  }, [fetchCommunityDetail])

  return { community, loading, error, refetch }
}
