import { useState, useRef, useCallback, useEffect } from 'react'

export function useOptimisticToggle(initialState = false, onSave = null, delayBeforeRequest = 800, onOptimisticChange = null) {
  const [state, setState] = useState(initialState)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  
  const serverStateRef = useRef(initialState)
  const timeoutRef = useRef(null)
  const previousStateRef = useRef(initialState)
  const onOptimisticChangeRef = useRef(onOptimisticChange)
  onOptimisticChangeRef.current = onOptimisticChange

  useEffect(() => {
    previousStateRef.current = initialState
    if (!timeoutRef.current) {
      serverStateRef.current = initialState
      setState(initialState)
    }
  }, [initialState])

  const executeAsync = useCallback(async (finalState) => {
    if (finalState === serverStateRef.current) return
    if (!onSave) return

    try {
      setError(null)
      setIsLoading(true)
      await onSave(finalState)
      serverStateRef.current = finalState
    } catch (err) {
      setError(err.message || 'Error en la petición')
      // Roll back here and in the parent, which applied the optimistic change too (BUG-045)
      previousStateRef.current = serverStateRef.current
      setState(serverStateRef.current)
      onOptimisticChangeRef.current?.(serverStateRef.current, { rollback: true })
    } finally {
      setIsLoading(false)
    }
  }, [onSave])

  const toggle = useCallback(() => {
    setError(null)

    const newState = !previousStateRef.current
    previousStateRef.current = newState

    if (onOptimisticChangeRef.current) {
      onOptimisticChangeRef.current(newState)
    }

    setState(newState)

    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    timeoutRef.current = setTimeout(() => {
      executeAsync(newState)
      timeoutRef.current = null
    }, delayBeforeRequest)
  }, [delayBeforeRequest, executeAsync])

  const cleanup = useCallback(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
    setIsLoading(false)
    setError(null)
  }, [])

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [])

  return { state, isLoading, error, toggle, cleanup, serverState: serverStateRef.current }
}