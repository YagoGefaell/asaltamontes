import { useEffect, useRef } from 'react'

/** Ref for the element at the end of a list: calls `onReach` when it scrolls into view (infinite list). */
export function useLoadMoreOnView(onReach, enabled) {
  const ref = useRef(null)
  const callbackRef = useRef(onReach)
  useEffect(() => { callbackRef.current = onReach })

  useEffect(() => {
    const node = ref.current
    if (!enabled || !node || typeof IntersectionObserver === 'undefined') return undefined
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) callbackRef.current()
    }, { rootMargin: '400px' })
    observer.observe(node)
    return () => observer.disconnect()
  }, [enabled])

  return ref
}
