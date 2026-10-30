import { apiClient, authApiClient } from '../../../lib/api-client.js'
import { setAccessToken, clearAccessToken, getAccessToken } from './auth-token.js'
import { storage } from '../../../utils/helpers.js'

function extractAccessToken(payload) {
  if (!payload) return null
  if (typeof payload === 'string') return payload

  const token = payload.accessToken ||
    payload.access_token ||
    payload.token ||
    payload.authorization ||
    payload.authToken ||
    payload?.data?.accessToken ||
    payload?.data?.token

  if (!token || typeof token !== 'string') return null
  return token.startsWith('Bearer ') ? token.slice('Bearer '.length) : token
}

export async function loginRequest({ username, password }) {
  try {
    const response = await authApiClient.post('/auth/login', { username, password })
    const accessToken = extractAccessToken(response)
    if (accessToken) {
      setAccessToken(accessToken)
    }
    return response
  } catch (error) {
    throw new Error(error.message || "Usuario o contraseña incorrectos")
  }
}

export async function loginWithGoogleRequest(code) {
  try {
    const response = await authApiClient.post('/auth/google', { code: code }, {
      headers: {
        'Content-Type': 'application/json'
      }
    })
    
    const accessToken = extractAccessToken(response)
    if (accessToken) {
      setAccessToken(accessToken)
    }
    return response;
  } catch (error) {
    throw new Error("Error al redirigir a Google " + (error.message || "Inténtalo de nuevo"));
  }
}

export async function registerRequest({ fullName, username, email, password, confirmPassword }) {
  try {
    const response = await authApiClient.post('/auth/register', { fullName, username, email, password, confirmPassword })
    const accessToken = extractAccessToken(response)
    if (accessToken) {
      setAccessToken(accessToken)
    }
    return response;
  } catch (error) {
    if (error.errors) {
      throw error;
    }
    throw new Error("Error al registrarse");
  }
}

// ---------------- EMAIL VERIFICATION (T24a2) ----------------
// Public: the token of the emailed link is the proof. 400 = invalid, used, replaced or expired.
export async function verifyEmailRequest(token) {
  await authApiClient.post('/auth/verify-email', { token })
}

// Sends the link again. 409 = already verified; 429 = too soon (error.retryAfter, in seconds).
export async function resendVerificationEmailRequest() {
  await apiClient.post('/api/users/me/verification-email')
}

// ---------------- PASSWORD RESET AND EMAIL CHANGE (T24b2) ----------------
// Always 202, whether or not the address has an account. 429 carries error.retryAfter.
export async function forgotPasswordRequest(email) {
  await authApiClient.post('/auth/forgot-password', { email })
}

// 400 = invalid link (or password), 409 = the two passwords differ, 429 = too many attempts.
export async function resetPasswordRequest({ token, password, confirmPassword }) {
  await authApiClient.post('/auth/reset-password', { token, password, confirmPassword })
}

// Needs the current password. 403 = wrong password, 409 = in use / same / Google account, 429 = too many.
export async function requestEmailChangeRequest({ newEmail, currentPassword }) {
  await apiClient.post('/api/users/me/email-change', { newEmail, currentPassword })
}

// Public: the token of the link sent to the new address. 400 = invalid or expired.
export async function confirmEmailChangeRequest(token) {
  await authApiClient.post('/auth/confirm-email-change', { token })
}

// ---------------- LOGOUT EVERYWHERE ----------------
// Revokes every session of the user (all devices). The caller then runs the normal logout.
export async function logoutEverywhereRequest() {
  try {
    await apiClient.post('/api/users/me/sessions/revoke-all')
  } catch {
    throw new Error("No se han podido cerrar las sesiones")
  }
}

// ---------------- LOGOUT ----------------
export async function logoutRequest() {
  try {
    await authApiClient.post('/auth/logout')
    return;
  } catch {
    throw new Error("Error al cerrar sesión")
  } finally {
    clearAccessToken()
    // Clear any localStorage data that might contain sensitive info
    storage.clearAppData()
  }
}

// ---------------- VERIFY SESSION ----------------
export async function verifySessionRequest() {
  // If no access token in memory, try refresh first to avoid 403 logs
  if (!getAccessToken()) {
    const refreshed = await refreshTokenRequest()
    if (!refreshed) return false
  }

  try {
    await apiClient.get('/api/users/me')
    return true
  } catch (error) {
    if (error.status !== 401 && error.status !== 403) {
      return false
    }

    // Token might be expired, try refresh once more
    const refreshed = await refreshTokenRequest()
    if (!refreshed) return false

    try {
      await apiClient.get('/api/users/me')
      return true
    } catch {
      return false
    }
  }
}

// ---------------- REFRESH TOKEN ----------------
// Returns false when the server rejected the refresh (401/403: the session is over). With
// rethrowTransient, network/timeout/5xx errors are thrown (marked `transient`) instead of reported as
// a rejected session, so the caller does not log the user out because of a deploy or a flaky network.
export async function refreshTokenRequest({ rethrowTransient = false } = {}) {
  try {
    const response = await authApiClient.post('/auth/refresh')
    const accessToken = extractAccessToken(response)
    if (accessToken) {
      setAccessToken(accessToken)
    }
    return true
  } catch (error) {
    if (rethrowTransient && error.status !== 401 && error.status !== 403) {
      error.transient = true
      throw error
    }
    return false
  }
}
