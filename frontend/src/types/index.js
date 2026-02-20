/**
 * Type definitions for better code documentation and IDE support
 * Note: These are JSDoc type definitions, not TypeScript
 */

/**
 * @typedef {Object} User
 * @property {number} id - User ID
 * @property {string} username - User's username
 * @property {string} email - User's email
 * @property {string} fullName - User's full name
 * @property {string?} avatar - User's avatar URL
 * @property {Date} createdAt - Account creation date
 * @property {Date} updatedAt - Last update date
 */

/**
 * @typedef {Object} AuthContextType
 * @property {boolean} isAuthenticated - Authentication status
 * @property {boolean} checkingAuth - Loading state for auth check
 * @property {Function} login - Login function
 * @property {Function} logout - Logout function
 * @property {Function} register - Register function
 * @property {Function} refreshToken - Refresh token function
 */

/**
 * @typedef {Object} UserProfileContextType
 * @property {User|null} userProfile - Current user profile
 * @property {boolean} loading - Loading state
 * @property {Function} updateUserProfile - Update profile function
 * @property {Function} searchUsersByUsername - Search users function
 */

/**
 * @typedef {Object} LoginCredentials
 * @property {string} username - Username
 * @property {string} password - Password
 */

/**
 * @typedef {Object} RegisterData
 * @property {string} fullName - Full name
 * @property {string} username - Username
 * @property {string} email - Email address
 * @property {string} password - Password
 * @property {string} confirmPassword - Password confirmation
 */

/**
 * @typedef {Object} FormState
 * @property {Object} values - Form values
 * @property {Object} errors - Form errors
 * @property {Object} touched - Touched fields
 * @property {boolean} isSubmitting - Submission state
 */

/**
 * @typedef {Object} AsyncState
 * @property {boolean} loading - Loading state
 * @property {any} data - Response data
 * @property {Error|null} error - Error state
 */

/**
 * @typedef {Object} ApiError
 * @property {string} message - Error message
 * @property {number} status - HTTP status code
 * @property {Object?} errors - Validation errors object
 * @property {string?} code - Error code
 */

export default {}