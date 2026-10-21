let accessToken = null

export function setAccessToken(token) {
  accessToken = token || null
}

export function getAccessToken() {
  return accessToken
}

export function clearAccessToken() {
  accessToken = null
}

/**
 * True if the JWT has no readable `exp` or expires within `marginSeconds` (T11: renew it before
 * reconnecting the WebSocket). Only reads the payload: the signature is the server's business.
 */
export function isTokenExpiringSoon(token, marginSeconds = 60, now = Date.now()) {
  if (!token) return true
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='))
    const { exp } = JSON.parse(json)
    if (typeof exp !== 'number') return true
    return exp * 1000 - now <= marginSeconds * 1000
  } catch {
    return true
  }
}
