import { useState, useRef, useCallback, useEffect } from 'react'

/**
 * Hook personalizado para manejar toggles optimistas con debounce SILENCIOSO.
 */
export function useOptimisticToggle(initialState = false, onSave = null, delayBeforeRequest = 800) {
  const [state, setState] = useState(initialState)
  // Dejamos el estado isLoading por compatibilidad, pero ya no lo activaremos durante el debounce
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)
  
  const serverStateRef = useRef(initialState)
  const timeoutRef = useRef(null)
  const previousStateRef = useRef(initialState)

  useEffect(() => {
    serverStateRef.current = initialState
    setState(initialState)
    previousStateRef.current = initialState
  }, [initialState])

  const executeAsync = useCallback(async (finalState) => {
    // FILTRO DE ARREPENTIMIENTO: Si volvió al estado inicial del servidor, cancelamos en seco
    if (finalState === serverStateRef.current) {
      return
    }

    if (!onSave) return

    try {
      setError(null)
      setIsLoading(true) // Solo se activa internamente si quisieras usarlo, pero no en el debounce
      await onSave(finalState)
      serverStateRef.current = finalState
    } catch (err) {
      // Revertir al estado del servidor si la API falla de verdad
      const errorMessage = err.message || 'Error en la petición'
      setError(errorMessage)
      setState(serverStateRef.current)
    } finally {
      setIsLoading(false)
    }
  }, [onSave])

  const toggle = useCallback(() => {
    setError(null)
    
    setState(prevState => {
      const newState = !prevState
      previousStateRef.current = newState
      
      // 1. Limpiar el temporizador anterior para acumular los clics rápidos
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
      
      // 2. MODIFICACIÓN CRÍTICA: Quitamos el "setIsLoading(true)" de aquí. 
      // El debounce ahora es completamente invisible para la UI.
      
      // 3. Establecer el nuevo timeout con el estado final tras el último clic
      timeoutRef.current = setTimeout(() => {
        executeAsync(newState)
        timeoutRef.current = null
      }, delayBeforeRequest)
      
      return newState
    })
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

  return {
    state,
    isLoading,
    error,
    toggle,
    cleanup,
    serverState: serverStateRef.current
  }
}