import { describe, expect, it } from 'vitest'
import { validateForm, validators } from './validation.js'

describe('validators', () => {
  it('email accepts a valid address and rejects malformed ones', () => {
    expect(validators.email('ana@example.com')).toBeNull()
    expect(validators.email('')).toBe('El email es requerido')
    expect(validators.email('ana@')).toBe('El formato del email no es válido')
  })

  it('username enforces length and allowed characters', () => {
    expect(validators.username('ana_trail')).toBeNull()
    expect(validators.username('')).toBe('El nombre de usuario es requerido')
    expect(validators.username('a')).toMatch(/al menos/)
    expect(validators.username('ana trail')).toMatch(/solo puede contener/)
  })

  it('password enforces minimum length', () => {
    expect(validators.password('')).toBe('La contraseña es requerida')
    expect(validators.password('1')).toMatch(/al menos/)
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
