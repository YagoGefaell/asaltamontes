/**
 * Validation utility functions
 */
import { VALIDATION } from '../config/constants.js'

export const validators = {
  /**
   * Validate email format
   */
  email: (email) => {
    if (!email) return 'El email es requerido'
    if (!VALIDATION.EMAIL.PATTERN.test(email)) {
      return 'El formato del email no es válido'
    }
    return null
  },

  /**
   * Validate username
   */
  username: (username) => {
    if (!username) return 'El nombre de usuario es requerido'
    if (username.length < VALIDATION.USERNAME.MIN_LENGTH) {
      return `El nombre de usuario debe tener al menos ${VALIDATION.USERNAME.MIN_LENGTH} caracteres`
    }
    if (username.length > VALIDATION.USERNAME.MAX_LENGTH) {
      return `El nombre de usuario no puede tener más de ${VALIDATION.USERNAME.MAX_LENGTH} caracteres`
    }
    if (!VALIDATION.USERNAME.PATTERN.test(username)) {
      return 'El nombre de usuario solo puede contener letras, números y guiones bajos'
    }
    return null
  },

  /**
   * Validate password
   */
  password: (password) => {
    if (!password) return 'La contraseña es requerida'
    if (password.length < VALIDATION.PASSWORD.MIN_LENGTH) {
      return `La contraseña debe tener al menos ${VALIDATION.PASSWORD.MIN_LENGTH} caracteres`
    }
    if (password.length > VALIDATION.PASSWORD.MAX_LENGTH) {
      return `La contraseña no puede tener más de ${VALIDATION.PASSWORD.MAX_LENGTH} caracteres`
    }
    return null
  },

  /**
   * Validate password confirmation
   */
  confirmPassword: (password, confirmPassword) => {
    if (!confirmPassword) return 'La confirmación de contraseña es requerida'
    if (password !== confirmPassword) {
      return 'Las contraseñas no coinciden'
    }
    return null
  },

  /**
   * Validate required field
   */
  required: (value) => {
    if (!value || (typeof value === 'string' && !value.trim())) {
      return 'Este campo es requerido'
    }
    return null
  },

  /**
   * Validate required field with custom message
   */
  requiredField: (value, fieldName) => {
    if (!value || (typeof value === 'string' && !value.trim())) {
      return `${fieldName} es requerido`
    }
    return null
  },

  /**
   * Validate full name
   */
  fullName: (fullName) => {
    if (!fullName) return 'El nombre completo es requerido'
    if (fullName.trim().length < 2) {
      return 'El nombre completo debe tener al menos 2 caracteres'
    }
    if (fullName.trim().length > 100) {
      return 'El nombre completo no puede tener más de 100 caracteres'
    }
    return null
  },
}

/**
 * Validate multiple fields at once
 */
export const validateForm = (data, rules) => {
  const errors = {}
  
  for (const [field, validationRules] of Object.entries(rules)) {
    for (const rule of validationRules) {
      const error = rule(data[field], data)
      if (error) {
        errors[field] = error
        break // Stop at first error for this field
      }
    }
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  }
}