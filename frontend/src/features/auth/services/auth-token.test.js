import { describe, expect, it } from 'vitest'
import { isTokenExpiringSoon } from './auth-token.js'

// Unsigned JWT with the given payload (only the payload is read)
function jwt(payload) {
  const encode = (value) => btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`
}

describe('isTokenExpiringSoon', () => {
  const now = Date.UTC(2026, 9, 21, 20, 0, 0)
  const inSeconds = (seconds) => Math.floor(now / 1000) + seconds

  it('is false for a token with more than the margin left', () => {
    expect(isTokenExpiringSoon(jwt({ sub: '1', exp: inSeconds(600) }), 60, now)).toBe(false)
  })

  it('is true within the margin and once expired', () => {
    expect(isTokenExpiringSoon(jwt({ exp: inSeconds(30) }), 60, now)).toBe(true)
    expect(isTokenExpiringSoon(jwt({ exp: inSeconds(-5) }), 60, now)).toBe(true)
  })

  it('is true when there is no token or it cannot be read', () => {
    expect(isTokenExpiringSoon(null)).toBe(true)
    expect(isTokenExpiringSoon('not-a-jwt')).toBe(true)
    expect(isTokenExpiringSoon(jwt({ sub: '1' }))).toBe(true)
  })

  it('reads base64url payloads (characters - and _)', () => {
    // "~~~" encodes to "fn5-" in base64url: forces the - character in the payload
    expect(isTokenExpiringSoon(jwt({ name: '~~~???', exp: inSeconds(600) }), 60, now)).toBe(false)
  })
})
