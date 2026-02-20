/**
 * Application constants and configuration
 */

// API Configuration
export const API_CONFIG = {
  BASE_URL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  ENDPOINTS: {
    // Auth endpoints
    AUTH: {
      LOGIN: '/auth/login',
      REGISTER: '/auth/register',
      LOGOUT: '/auth/logout',
      VERIFY: '/auth/verify',
      REFRESH: '/auth/refresh',
    },
    // User endpoints
    USERS: {
      ME: '/api/users/me',
      SEARCH: '/api/users/search',
    },
  },
  TIMEOUT: 10000, // 10 seconds
}

// App Configuration
export const APP_CONFIG = {
  NAME: 'Asaltamontes Female',
  VERSION: '1.0.0',
  DESCRIPTION: 'Conecta, comparte y crece con otras mujeres como tú.',
}

// Route paths
export const ROUTES = {
  // Public routes
  ACCESS: '/access',
  LOGIN: '/login',
  REGISTER: '/register',
  
  // Private routes
  HOME: '/home',
  PROFILE: '/me',
  SEARCH: '/search',
  LOGOUT: '/logout',
  
  // Settings routes
  SETTINGS: {
    ROOT: '/settings',
    PROFILE: '/settings/profile',
    PREFERENCES: '/settings/preferences',
    NOTIFICATIONS: '/settings/notifications',
    COURSES: '/settings/courses',
    LINKEDIN: '/settings/linkedin',
    SCHOOLS: '/settings/schools',
    SOCIAL: '/settings/social',
    PRIVACY: '/settings/privacy',
    PLAN: '/settings/plan',
  },
}

// UI Constants
export const UI_CONFIG = {
  BREAKPOINTS: {
    SM: '640px',
    MD: '768px',
    LG: '1024px',
    XL: '1280px',
  },
  ANIMATIONS: {
    FAST: '150ms',
    NORMAL: '300ms',
    SLOW: '500ms',
  },
}

// Validation rules
export const VALIDATION = {
  USERNAME: {
    MIN_LENGTH: 3,
    MAX_LENGTH: 20,
    PATTERN: /^[a-zA-Z0-9_]+$/,
  },
  PASSWORD: {
    MIN_LENGTH: 8,
    MAX_LENGTH: 100,
  },
  EMAIL: {
    PATTERN: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  },
}