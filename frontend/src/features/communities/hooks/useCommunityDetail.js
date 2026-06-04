import { useState, useEffect, useCallback } from 'react'
import { getCommunityByNameRequest } from '../services/communities.service'

export function useCommunityDetail(communityName) {
  const [community, setCommunity] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!communityName) {
      setError('Nombre de comunidad inválido')
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    const fetchCommunityDetail = async () => {
      try {
        const communityData = await getCommunityByNameRequest(communityName)
        setCommunity(communityData)
        setError(null)
      } catch (err) {
        setError(err.message || 'Error cargando comunidad')
        setCommunity(null)
      } finally {
        setLoading(false)
      }
    }

    fetchCommunityDetail()
  }, [communityName])

  const refetch = useCallback(async () => {
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
      setError(null)
    } catch (err) {
      setError(err.message || 'Error cargando comunidad')
      setCommunity(null)
    } finally {
      setLoading(false)
    }
  }, [communityName])

  return { community, setCommunity, loading, error, refetch }
}
