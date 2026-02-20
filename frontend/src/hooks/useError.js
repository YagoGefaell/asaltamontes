import { useState, useCallback } from 'react'

/**
 * Hook for managing local component errors
 */
export const useError = () => {
  const [error, setError] = useState(null)

  const setErrorMessage = useCallback((message) => {
    setError(message)
  }, [])

  const clearError = useCallback(() => {
    setError(null)
  }, [])

  const handleError = useCallback((err) => {
    console.error('Error caught:', err)
    
    // Handle different types of errors
    if (err?.message) {
      setError(err.message)
    } else if (typeof err === 'string') {
      setError(err)
    } else {
      setError('Ha ocurrido un error inesperado')
    }
  }, [])

  return {
    error,
    setError: setErrorMessage,
    clearError,
    handleError,
    hasError: !!error,
  }
}