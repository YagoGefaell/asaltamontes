import { describe, expect, it } from 'vitest'
import { REGISTER_RULES, validateForm, validators } from './validation.js'

describe('validators', () => {
  it('email accepts a valid address and rejects malformed ones', () => {
    expect(validators.email('ana@example.com')).toBeNull()
    expect(validators.email('')).toBe('El email es requerido')
    expect(validators.email('ana@')).toBe('Formato de email inválido')
  })

  it('username follows the backend rules: 3-30, letters, digits, dots and underscores', () => {
    expect(validators.username('ana_trail')).toBeNull()
    expect(validators.username('ana.trail')).toBeNull()
    expect(validators.username('')).toBe('El nombre de usuaria es requerido')
    expect(validators.username('an')).toBe('Debe tener entre 3 y 30 caracteres')
    expect(validators.username('a'.repeat(31))).toBe('Debe tener entre 3 y 30 caracteres')
    expect(validators.username('ana trail')).toMatch(/Solo letras/)
    expect(validators.username('.ana')).toMatch(/sin punto/)
    expect(validators.username('ana.')).toMatch(/sin punto/)
  })

  it('password must have 8 to 100 characters', () => {
    expect(validators.password('')).toBe('La contraseña es requerida')
    expect(validators.password('1234567')).toBe('Debe tener entre 8 y 100 caracteres')
    expect(validators.password('12345678')).toBeNull()
  })

  it('fullName accepts accents, hyphens and apostrophes but not digits', () => {
    expect(validators.fullName("María-José O'Neill")).toBeNull()
    expect(validators.fullName('A')).toBe('Debe tener entre 2 y 60 caracteres')
    expect(validators.fullName('Ana 2')).toBe('Solo letras, espacios, guiones y apóstrofos')
    expect(validators.fullName('-Ana')).toBe('Solo letras, espacios, guiones y apóstrofos')
  })

  it('confirmPassword requires both passwords to match', () => {
    expect(validators.confirmPassword('secret123', 'secret123')).toBeNull()
    expect(validators.confirmPassword('secret123', 'other')).toBe('Las contraseñas no coinciden')
  })

  it('required treats blank strings as empty', () => {
    expect(validators.required('   ')).toBe('Este campo es requerido')
    expect(validators.required('x')).toBeNull()
  })
})

describe('validateForm', () => {
  it('returns the first error of each invalid field', () => {
    const result = validateForm(
      { email: '', username: 'ana_trail' },
      {
        email: [validators.required, validators.email],
        username: [validators.required, validators.username],
      },
    )

    expect(result.isValid).toBe(false)
    expect(result.errors).toEqual({ email: 'Este campo es requerido' })
  })

  it('is valid when every rule passes', () => {
    const result = validateForm({ email: 'ana@example.com' }, { email: [validators.email] })

    expect(result).toEqual({ isValid: true, errors: {} })
  })
})

describe('REGISTER_RULES', () => {
  const valid = {
    fullName: 'Ana Pérez',
    username: 'ana.perez',
    email: 'ana@example.com',
    password: 'password123',
    confirmPassword: 'password123',
  }

  it('accepts a valid registration', () => {
    expect(validateForm(valid, REGISTER_RULES).isValid).toBe(true)
  })

  it('reports every invalid field like the backend does', () => {
    const { isValid, errors } = validateForm(
      { fullName: 'A1', username: '.ana', email: 'nope', password: 'short', confirmPassword: 'other' },
      REGISTER_RULES,
    )

    expect(isValid).toBe(false)
    expect(Object.keys(errors).sort()).toEqual(['confirmPassword', 'email', 'fullName', 'password', 'username'])
  })
})
