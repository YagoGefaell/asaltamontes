import { useState, useEffect, useCallback } from 'react'
import { getCommunityRequest } from '../services/communities.service'

export function useCommunityDetail(slug) {
  const [community, setCommunity] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!slug) {
      setError('Nombre de comunidad inválido')
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    const fetchCommunityDetail = async () => {
      try {
        const communityData = await getCommunityRequest(slug)
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
  }, [slug])

  const refetch = useCallback(async () => {
    if (!slug) {
      setError('Nombre de comunidad inválido')
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)

    try {
      const communityData = await getCommunityRequest(slug)
      setCommunity(communityData)
      setError(null)
    } catch (err) {
      setError(err.message || 'Error cargando comunidad')
      setCommunity(null)
    } finally {
      setLoading(false)
    }
  }, [slug])

  return { community, setCommunity, loading, error, refetch }
}
