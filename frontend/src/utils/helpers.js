export const formatters = {

  capitalize: (str) => {
    return str
      .toLowerCase()
      .split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')
  },

  truncate: (str, maxLength = 50) => {
    if (str.length <= maxLength) return str
    return str.slice(0, maxLength).trim() + '...'
  },

  username: (username) => {
    return username.toLowerCase().trim()
  },

  avatar: (profilePictureUrl, fullName, username) => {
    if (profilePictureUrl) return profilePictureUrl;
    
    const name = fullName || username || 'Usuario';
    const initial = name.trim().charAt(0).toUpperCase() || '?';
    return `https://ui-avatars.com/api/?name=${encodeURIComponent(initial)}&background=178B9F&color=fff&size=200&bold=true`;
  },

  getInitial: (fullName, username) => {
    const name = fullName || username || 'Usuario';
    return name.trim().charAt(0).toUpperCase() || '?';
  },
}

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

  clearAppData: () => {
    try {
      const appKeys = [
        'user', 'auth', 'token', 'session', 'profile',
        'settings', 'preferences', 'theme', 'language',
        'asaltamontes', 'app-state', 'redux-persist'
      ]

      appKeys.forEach(key => {
        if (localStorage.getItem(key)) {
          localStorage.removeItem(key)
        }
      })

      Object.keys(localStorage).forEach(key => {
        if (key.toLowerCase().includes('asaltamontes') || 
            key.toLowerCase().includes('auth') ||
            key.toLowerCase().includes('user')) {
          localStorage.removeItem(key)
        }
      })

      return true
    } catch (error) {
      console.warn('Error clearing app localStorage:', error)
      return false
    }
  },
}
