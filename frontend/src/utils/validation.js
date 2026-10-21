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
      return 'Formato de email inválido'
    }
    return null
  },

  /**
   * Validate username
   */
  username: (username) => {
    if (!username) return 'El nombre de usuario es requerido'
    if (username.length < VALIDATION.USERNAME.MIN_LENGTH || username.length > VALIDATION.USERNAME.MAX_LENGTH) {
      return `Debe tener entre ${VALIDATION.USERNAME.MIN_LENGTH} y ${VALIDATION.USERNAME.MAX_LENGTH} caracteres`
    }
    if (!VALIDATION.USERNAME.PATTERN.test(username)) {
      return 'Solo letras, números, puntos y guiones bajos, sin punto al principio ni al final'
    }
    return null
  },

  /**
   * Validate password
   */
  password: (password) => {
    if (!password) return 'La contraseña es requerida'
    if (password.length < VALIDATION.PASSWORD.MIN_LENGTH || password.length > VALIDATION.PASSWORD.MAX_LENGTH) {
      return `Debe tener entre ${VALIDATION.PASSWORD.MIN_LENGTH} y ${VALIDATION.PASSWORD.MAX_LENGTH} caracteres`
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
    if (!fullName || !fullName.trim()) return 'El nombre completo es requerido'
    const name = fullName.trim()
    if (name.length < VALIDATION.FULL_NAME.MIN_LENGTH || name.length > VALIDATION.FULL_NAME.MAX_LENGTH) {
      return `Debe tener entre ${VALIDATION.FULL_NAME.MIN_LENGTH} y ${VALIDATION.FULL_NAME.MAX_LENGTH} caracteres`
    }
    if (!VALIDATION.FULL_NAME.PATTERN.test(name)) {
      return 'Solo letras, espacios, guiones y apóstrofos'
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

/**
 * Registration rules, the same ones the backend applies (POST /auth/register)
 */
export const REGISTER_RULES = {
  fullName: [validators.fullName],
  username: [validators.username],
  email: [validators.email],
  password: [validators.password],
  confirmPassword: [(confirmPassword, data) => validators.confirmPassword(data.password, confirmPassword)],
}
