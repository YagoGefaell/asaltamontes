import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/hooks/useAuth'
import { locateCity } from '../services/places.service.js'

/**
 * Centre of the city in her profile, located in the background when the form opens, so the place
 * suggestions come out near her before she marks anything. Null while loading, without a city or on error.
 */
export function useHomeArea() {
  const { currentUser } = useAuth()
  const city = currentUser?.city?.trim() || ''
  const [area, setArea] = useState(null)

  useEffect(() => {
    if (!city) return undefined
    const controller = new AbortController()
    locateCity(city, { signal: controller.signal })
      .then(setArea)
      .catch(() => {}) // without a reference the suggestions are just less local
    return () => controller.abort()
  }, [city])

  return city ? area : null
}
