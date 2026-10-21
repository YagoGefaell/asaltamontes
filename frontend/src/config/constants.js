/**
 * Application constants and configuration
 */

// Empty = same origin: in production nginx proxies /api, /auth and /ws-asaltamontes to the backend,
// and in development the Vite dev server does the same (vite.config.js). Set VITE_API_URL only to
// point the frontend at a backend on another origin.
const ENV_API_URL = import.meta.env.VITE_API_URL?.trim();
const RESOLVED_API_URL = (ENV_API_URL || "").replace(/\/$/, "");

// API Configuration
export const API_CONFIG = {
  BASE_URL: RESOLVED_API_URL,
  ENDPOINTS: {
    // Auth endpoints
    AUTH: {
      LOGIN: '/auth/login',
      REGISTER: '/auth/register',
      LOGOUT: '/auth/logout',
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

// Chat (same limit as the backend, MessageService.MAX_CONTENT_LENGTH)
export const CHAT = {
  MAX_MESSAGE_LENGTH: 2000,
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
  USER_PROFILE: '/users', // Para `/users/:username`
  
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

// Validation rules: mirror of the backend's UserFieldRules (keep both in sync)
export const VALIDATION = {
  USERNAME: {
    MIN_LENGTH: 3,
    MAX_LENGTH: 30,
    // Letters, digits, dots and underscores; no dot at the start or at the end
    PATTERN: /^(?![.])[a-zA-Z0-9._]+(?<![.])$/,
  },
  PASSWORD: {
    MIN_LENGTH: 8,
    MAX_LENGTH: 100,
  },
  FULL_NAME: {
    MIN_LENGTH: 2,
    MAX_LENGTH: 60,
    // Letters of any language, spaces, hyphens and apostrophes, starting with a letter
    PATTERN: /^\p{L}[\p{L} '-]*$/u,
  },
  EMAIL: {
    PATTERN: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  },
}