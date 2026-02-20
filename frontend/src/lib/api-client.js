import { API_CONFIG } from '../config/constants.js'

/**
 * Custom fetch wrapper with improved error handling and configuration
 */
class ApiClient {
  constructor(baseURL = API_CONFIG.BASE_URL) {
    this.baseURL = baseURL
    this.timeout = API_CONFIG.TIMEOUT
  }

  /**
   * Create fetch request with timeout and common options
   */
  async request(endpoint, options = {}) {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), this.timeout)

    const config = {
      credentials: 'include', // Crucial: include cookies
      mode: 'cors', // Enable CORS
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...options.headers,
      },
      signal: controller.signal,
      ...options,
    }

    try {
      // Enhanced logging for debugging
      if (process.env.NODE_ENV === 'development') {
        console.log(`API: ${config.method || 'GET'} ${endpoint}`);
        // Log cookies being sent
        console.log('📤 Cookies being sent:', document.cookie || 'NO COOKIES');
      }
      
      const response = await fetch(`${this.baseURL}${endpoint}`, config)
      clearTimeout(timeoutId)
      
      return await this.handleResponse(response)
    } catch (error) {
      clearTimeout(timeoutId)
      throw this.handleError(error)
    }
  }

  /**
   * Handle response and extract data
   */
  async handleResponse(response) {
    // Enhanced logging for auth endpoints
    if (process.env.NODE_ENV === 'development' && response.url.includes('auth')) {
      console.group('📥 Auth Response Details');
      console.log('Status:', response.status);
      console.log('URL:', response.url);
      
      // Log all response headers
      console.log('All headers:');
      for (let [key, value] of response.headers.entries()) {
        console.log(`  ${key}: ${value}`);
        // Special attention to Set-Cookie
        if (key.toLowerCase() === 'set-cookie') {
          console.log(`🍪 FOUND Set-Cookie: ${value}`);
        }
      }
      
      // Multiple attempts to get Set-Cookie
      const setCookieHeader = response.headers.get('Set-Cookie');
      const setCookieLower = response.headers.get('set-cookie');
      console.log('Set-Cookie (case sensitive):', setCookieHeader);
      console.log('set-cookie (lowercase):', setCookieLower);
      
      // Check if cookies changed
      console.log('Document.cookie before checking:', document.cookie || 'NO COOKIES');
      
      // Wait a moment and check again
      setTimeout(() => {
        console.log('Document.cookie 100ms later:', document.cookie || 'NO COOKIES');
      }, 100);
      
      console.groupEnd();
    }
    
    if (response.ok) {
      const contentType = response.headers.get('content-type')
      
      if (contentType && contentType.includes('application/json')) {
        return await response.json()
      }
      
      const text = await response.text()
      return text || null
    }

    // Handle different error status codes
    let errorMessage = 'An error occurred'
    
    try {
      const errorData = await response.json()
      errorMessage = errorData.message || errorData.error || errorMessage
      
      // For validation errors, preserve the structure
      if (response.status === 400 && errorData.errors) {
        const error = new Error(errorMessage)
        error.status = response.status
        error.errors = errorData.errors
        throw error
      }
    } catch (parseError) {
      // If parsing JSON fails, use status text or generic message based on status code
      if (response.status >= 500) {
        errorMessage = 'Error del servidor. Por favor, inténtalo más tarde.'
      } else if (response.status === 401) {
        errorMessage = 'No autorizado'
      } else if (response.status === 403) {
        errorMessage = 'Acceso denegado'
      } else if (response.status === 404) {
        errorMessage = 'Recurso no encontrado'
      } else {
        errorMessage = response.statusText || errorMessage
      }
    }

    const error = new Error(errorMessage)
    error.status = response.status
    throw error
  }

  /**
   * Handle different types of errors
   */
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

// Create and export default instance
export const apiClient = new ApiClient()

// Export class for custom instances if needed
export default ApiClient