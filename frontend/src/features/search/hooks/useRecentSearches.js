import { useCallback, useState } from 'react'

export const RECENT_KEY = 'asaltamontes.recentSearches'
export const MAX_RECENTS = 8

// localStorage can throw (private window, blocked site data) or hold garbage: never break the page for it
function read() {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(RECENT_KEY) ?? '[]')
    return Array.isArray(parsed) ? parsed.filter((t) => typeof t === 'string' && t.trim()).slice(0, MAX_RECENTS) : []
  } catch {
    return []
  }
}

function write(list) {
  try {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(list))
  } catch {
    // ignored: recents are a convenience
  }
}

/** Last searches of this browser (most recent first, no duplicates). */
export function useRecentSearches() {
  const [recents, setRecents] = useState(read)

  const add = useCallback((term) => {
    const clean = (term ?? '').trim()
    if (clean.length < 2) return
    setRecents((prev) => {
      const next = [clean, ...prev.filter((t) => t.toLowerCase() !== clean.toLowerCase())].slice(0, MAX_RECENTS)
      write(next)
      return next
    })
  }, [])

  const remove = useCallback((term) => {
    setRecents((prev) => {
      const next = prev.filter((t) => t !== term)
      write(next)
      return next
    })
  }, [])

  const clear = useCallback(() => {
    write([])
    setRecents([])
  }, [])

  return { recents, add, remove, clear }
}
