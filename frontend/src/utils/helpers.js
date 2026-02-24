/**
 * Common utility functions
 */

/**
 * Format text utilities
 */
export const formatters = {
  /**
   * Capitalize first letter of each word
   */
  capitalize: (str) => {
    return str
      .toLowerCase()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
  },

  /**
   * Truncate text with ellipsis
   */
  truncate: (str, maxLength = 50) => {
    if (str.length <= maxLength) return str
    return str.slice(0, maxLength).trim() + '...'
  },

  /**
   * Clean and format username
   */
  username: (username) => {
    return username.toLowerCase().trim()
  },

  /**
   * Generate avatar URL with fallback to UI Avatars
   */
  avatar: (profilePictureUrl, fullName, username) => {
    if (profilePictureUrl) return profilePictureUrl;
    
    const name = fullName || username || 'Usuario';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=ff4f8b&color=fff&size=200&bold=true`;
  },
}

/**
 * Local storage utilities with error handling
 */
export const storage = {
  set: (key, value) => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
      return true
    } catch (error) {
      console.warn('Error saving to localStorage:', error)
      return false
    }
  },

  get: (key, defaultValue = null) => {
    try {
      const item = localStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch (error) {
      console.warn('Error reading from localStorage:', error)
      return defaultValue
    }
  },

  remove: (key) => {
    try {
      localStorage.removeItem(key)
      return true
    } catch (error) {
      console.warn('Error removing from localStorage:', error)
      return false
    }
  },

  clear: () => {
    try {
      localStorage.clear()
      return true
    } catch (error) {
      console.warn('Error clearing localStorage:', error)
      return false
    }
  },

  /**
   * Clear application-specific localStorage data
   */
  clearAppData: () => {
    try {
      // Common keys that might be stored by the app
      const appKeys = [
        'user', 'auth', 'token', 'session', 'profile',
        'settings', 'preferences', 'theme', 'language',
        'asaltamontes', 'app-state', 'redux-persist'
      ]
      
      // Remove each key if it exists
      appKeys.forEach(key => {
        if (localStorage.getItem(key)) {
          localStorage.removeItem(key)
        }
      })
      
      // Also remove any keys that start with app name
      Object.keys(localStorage).forEach(key => {
        if (key.toLowerCase().includes('asaltamontes') || 
            key.toLowerCase().includes('auth') ||
            key.toLowerCase().includes('user')) {
          localStorage.removeItem(key)
        }
      })
      
      console.log('✅ Application localStorage cleaned')
      return true
    } catch (error) {
      console.warn('Error clearing app localStorage:', error)
      return false
    }
  },
}

/**
 * Async utilities
 */
export const asyncUtils = {
  /**
   * Debounce function calls
   */
  debounce: (func, delay) => {
    let timeoutId
    return (...args) => {
      clearTimeout(timeoutId)
      timeoutId = setTimeout(() => func.apply(null, args), delay)
    }
  },

  /**
   * Throttle function calls
   */
  throttle: (func, limit) => {
    let inThrottle
    return (...args) => {
      if (!inThrottle) {
        func.apply(null, args)
        inThrottle = true
        setTimeout(() => (inThrottle = false), limit)
      }
    }
  },

  /**
   * Sleep/delay function
   */
  sleep: (ms) => new Promise(resolve => setTimeout(resolve, ms)),
}

/**
 * DOM utilities
 */
export const domUtils = {
  /**
   * Check if element is in viewport
   */
  isInViewport: (element) => {
    const rect = element.getBoundingClientRect()
    return (
      rect.top >= 0 &&
      rect.left >= 0 &&
      rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
      rect.right <= (window.innerWidth || document.documentElement.clientWidth)
    )
  },

  /**
   * Scroll to element smoothly
   */
  scrollToElement: (element, offset = 0) => {
    const y = element.getBoundingClientRect().top + window.pageYOffset + offset
    window.scrollTo({ top: y, behavior: 'smooth' })
  },
}

/**
 * URL utilities
 */
export const urlUtils = {
  /**
   * Build query string from object
   */
  buildQuery: (params) => {
    const query = new URLSearchParams()
    
    Object.entries(params).forEach(([key, value]) => {
      if (value !== null && value !== undefined && value !== '') {
        query.append(key, String(value))
      }
    })
    
    return query.toString()
  },

  /**
   * Parse query string to object
   */
  parseQuery: (queryString = window.location.search) => {
    const params = new URLSearchParams(queryString)
    const result = {}
    
    for (const [key, value] of params) {
      result[key] = value
    }
    
    return result
  },
}

// Make localStorage cleanup available globally for debugging
if (typeof window !== 'undefined') {
  window.clearAppStorage = () => {
    storage.clearAppData()
    console.log('🧹 Application storage cleared manually')
  }
  
  window.inspectStorage = () => {
    console.group('📦 Storage Inspector')
    console.log('localStorage keys:', Object.keys(localStorage))
    console.log('localStorage size:', new Blob(Object.values(localStorage)).size + ' bytes')
    Object.keys(localStorage).forEach(key => {
      console.log(`${key}:`, localStorage.getItem(key)?.substring(0, 100) + '...')
    })
    console.groupEnd()
  }
  
  console.log('🛠️ Storage utilities available: clearAppStorage() and inspectStorage()')
}