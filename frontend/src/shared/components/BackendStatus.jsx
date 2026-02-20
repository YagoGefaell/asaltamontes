import { useState, useEffect } from 'react'
import { apiClient } from '../../lib/api-client.js'
import './BackendStatus.css'

/**
 * Component to check and display backend connectivity status
 */
function BackendStatus() {
  const [status, setStatus] = useState('checking') // 'checking', 'online', 'offline'
  const [error, setError] = useState(null)

  useEffect(() => {
    const checkBackendStatus = async () => {
      try {
        // Try a simple endpoint to check if backend is reachable
        // Don't use /auth/verify as it expects cookies
        await apiClient.get('/auth/verify')
        setStatus('online')
        setError(null)
      } catch (err) {
        if (err.code === 'NETWORK_ERROR' || err.code === 'TIMEOUT') {
          setStatus('offline')
          setError('No se puede conectar al servidor')
        } else if (err.status >= 500) {
          setStatus('offline')
          setError('El servidor está experimentando problemas')
        } else if (err.status === 401) {
          // 401 is expected when not authenticated - backend is working
          setStatus('online')
          setError(null)
        } else {
          setStatus('online')
          setError(null)
        }
      }
    }

    // Only check once on mount
    checkBackendStatus()
  }, [])

  if (status === 'checking') {
    return (
      <div className="backend-status checking">
        <span className="status-indicator"></span>
        <span className="status-text">Verificando conexión...</span>
      </div>
    )
  }

  if (status === 'offline') {
    return (
      <div className="backend-status offline">
        <span className="status-indicator"></span>
        <div className="status-content">
          <span className="status-text">Servidor no disponible</span>
          <p className="status-description">{error}</p>
          <p className="status-help">
            Asegúrate de que el backend esté ejecutándose en http://localhost:8080
          </p>
        </div>
      </div>
    )
  }

  // Don't show anything when online
  return null
}

export default BackendStatus