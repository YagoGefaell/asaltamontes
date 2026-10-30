import { useEffect, useState } from 'react'
import { getAdminReportsRequest } from '../services/admin.service.js'

/**
 * Whether the signed-in user can use the moderation panel. The backend decides (ADMIN or MODERATOR, read from the
 * database on every request): the queue answers 403 to anyone else. Returns 'checking' | 'staff' | 'denied'.
 */
export function useIsStaff() {
  const [state, setState] = useState('checking')

  useEffect(() => {
    let active = true
    getAdminReportsRequest({ limit: 1 })
      .then(() => active && setState('staff'))
      .catch(() => active && setState('denied'))
    return () => {
      active = false
    }
  }, [])

  return state
}
