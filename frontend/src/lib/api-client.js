import { API_CONFIG } from '../config/constants.js'
import { getAccessToken } from '../features/auth/services/auth-token.js'

class ApiClient {
  constructor(enableAutoRefresh = true, baseURL = API_CONFIG.BASE_URL) {
    this.baseURL = baseURL
    this.timeout = API_CONFIG.TIMEOUT
    this.enableAutoRefresh = enableAutoRefresh
    this.isRefreshing = false
    this.refreshPromise = null
    this.logoutCallback = null
  }

  async request(endpoint, options = {}) {
    return this._makeRequest(endpoint, options, false)
  }

  async _makeRequest(endpoint, options = {}, isRetry = false) {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this.timeout)

    const accessToken = getAccessToken()
    const authHeader = accessToken && !this._isTokenlessEndpoint(endpoint)
      ? { Authorization: `Bearer ${accessToken}` }
      : {}

    const config = {
      credentials: 'include',
      mode: 'cors',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...authHeader,
        ...options.headers,
      },
      signal: controller.signal,
      ...options,
    }

    try {
      const response = await fetch(`${this.baseURL}${endpoint}`, config)
      clearTimeout(timeoutId)
      
      if ((response.status === 401 || response.status === 403) && 
          !isRetry && 
          this.enableAutoRefresh && 
          !this._isAuthEndpoint(endpoint)) {
        try {
          const refreshSuccess = await this._handleTokenRefresh()
          if (refreshSuccess) {
            return this._makeRequest(endpoint, options, true)
          } else {
            const error = new Error('Session expired')
            error.status = response.status
            throw error
          }
        } catch (refreshError) {
          if (refreshError.status) {
            throw refreshError
          } else {
            const error = new Error('Authentication failed')
            error.status = response.status
            throw error
          }
        }
      }
      
      return await this.handleResponse(response)
    } catch (error) {
      clearTimeout(timeoutId)
      throw this.handleError(error)
    }
  }

  async _handleTokenRefresh() {
    if (this.isRefreshing && this.refreshPromise) {
      return await this.refreshPromise
    }

    this.isRefreshing = true
    this.refreshPromise = this._performRefresh()
    
    try {
      const result = await this.refreshPromise
      return result
    } finally {
      this.isRefreshing = false
      this.refreshPromise = null
    }
  }

  async _performRefresh() {
    try {
      const { refreshTokenRequest } = await import('../features/auth/services/auth.service.js')
      const success = await refreshTokenRequest()
      
      if (success) {
        return true
      } else {
        this._handleLogout()
        return false
      }
    } catch (error) {
      this._handleLogout()
      return false
    }
  }

  _isAuthEndpoint(endpoint) {
    const authEndpoints = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']
    return authEndpoints.some(authPath => endpoint.includes(authPath))
  }

  _isTokenlessEndpoint(endpoint) {
    const tokenlessEndpoints = ['/auth/login', '/auth/register', '/auth/refresh']
    return tokenlessEndpoints.some(authPath => endpoint.includes(authPath))
  }

  setLogoutCallback(callback) {
    this.logoutCallback = callback
  }

  _handleLogout() {
    if (this.logoutCallback) {
      this.logoutCallback()
    }
  }

  /**
   * Enhanced cookie debugging
   */
  _logCookieDetails(context) {
    console.group(`🍪 Cookie Debug - ${context}`)
    console.log('Raw document.cookie:', document.cookie)
    
    if (document.cookie) {
      const cookies = document.cookie.split(';')
      console.log('Parsed cookies:')
      cookies.forEach(cookie => {
        const [name, value] = cookie.trim().split('=')
        console.log(`  ${name}: ${value?.substring(0, 50)}...`)
      })
    } else {
      console.log('❌ NO COOKIES FOUND')
    }
    console.groupEnd()
  }

  async handleResponse(response) {
    if (response.ok) {
      const contentType = response.headers.get('content-type')
      
      if (contentType && contentType.includes('application/json')) {
        return await response.json()
      }
      
      const text = await response.text()
      return text || null
    }

    let errorMessage = 'An error occurred'
    let errors = null

    try {
      const errorData = await response.json()
      // ProblemDetail uses 'detail', but also support 'message' and 'error' for compatibility
      errorMessage = errorData.detail || errorData.message || errorData.error || errorMessage
      
      // Extract individual field errors if present
      if (errorData.errors) {
        errors = errorData.errors
      }
    } catch (parseError) {
      // JSON parsing failed, use a generic message based on status
      if (response.status >= 500) {
        errorMessage = 'Error del servidor. Por favor, inténtalo más tarde.'
      } else if (response.status === 401) {
        errorMessage = 'No autorizado'
      } else if (response.status === 403) {
        errorMessage = 'Acceso denegado'
      } else if (response.status === 404) {
        errorMessage = 'Recurso no encontrado'
      } else if (response.status === 409) {
        errorMessage = 'El usuario ya existe'
      } else {
        errorMessage = response.statusText || errorMessage
      }
    }

    const error = new Error(errorMessage)
    error.status = response.status
    
    // Attach field errors if they exist
    if (errors) {
      error.errors = errors
    }
    
    throw error
  }

  handleError(error) {
    if (error.name === 'AbortError') {
      const timeoutError = new Error('Request timeout')
      timeoutError.code = 'TIMEOUT'
      return timeoutError
    }

    if (!navigator.onLine) {
      const networkError = new Error('No internet connection')
      networkError.code = 'NETWORK_ERROR'
      return networkError
    }

    return error
  }

  // HTTP Methods
  async get(endpoint, options = {}) {
    return this.request(endpoint, { method: 'GET', ...options })
  }

  async post(endpoint, data, options = {}) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
      ...options,
    })
  }

  async put(endpoint, data, options = {}) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
      ...options,
    })
  }

  async patch(endpoint, data, options = {}) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(data),
      ...options,
    })
  }

  async delete(endpoint, options = {}) {
    return this.request(endpoint, { method: 'DELETE', ...options })
  }
}

// Create and export instances
export const apiClient = new ApiClient()

// Auth client without automatic refresh (to avoid infinite loops)
export const authApiClient = new ApiClient(false)

export default apiClient
export { ApiClient }