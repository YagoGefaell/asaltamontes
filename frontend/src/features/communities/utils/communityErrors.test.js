import { describe, expect, it } from 'vitest'
import { communityErrorMessage, formatWait } from './communityErrors'

describe('communityErrorMessage', () => {
  it('shows the Retry-After wait on 429', () => {
    expect(communityErrorMessage({ status: 429, retryAfter: 7200 })).toContain('2 horas')
    expect(communityErrorMessage({ status: 429, retryAfter: 90000 })).toContain('2 días')
  })
  it('has a message for 429 without Retry-After', () => {
    expect(communityErrorMessage({ status: 429 })).toContain('más tarde')
  })
  it('keeps the neutral backend message on 403 and explains 404', () => {
    expect(communityErrorMessage({ status: 403, message: 'No se puede completar esta acción' })).toBe('No se puede completar esta acción')
    expect(communityErrorMessage({ status: 403 })).toBe('No tienes permiso para hacer esto.')
    expect(communityErrorMessage({ status: 404 })).toMatch(/ya no existe/)
  })
  it('formats waits', () => {
    expect(formatWait(1)).toBe('1 segundo')
    expect(formatWait(61)).toBe('2 minutos')
    expect(formatWait(0)).toBeNull()
  })
})
