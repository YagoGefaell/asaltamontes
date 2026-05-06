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
export async function refreshTokenRequest() {
  try {
    const response = await authApiClient.post('/auth/refresh')
    const accessToken = extractAccessToken(response)
    if (accessToken) {
      setAccessToken(accessToken)
    }
    return true
  } catch {
    return false
  }
}
