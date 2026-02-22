import { authApiClient } from '../../../lib/api-client.js'

export async function loginRequest({ username, password }) {
  try {
    await authApiClient.post('/auth/login', { username, password })
    return;
  } catch (error) {
    throw new Error(error.message || "Usuario o contraseña incorrectos")
  }
}

export async function registerRequest({ fullName, username, email, password, confirmPassword }) {
  try {
    await authApiClient.post('/auth/register', { fullName, username, email, password, confirmPassword })
    return;
  } catch (error) {
    throw error
  }
}

// ---------------- LOGOUT ----------------
export async function logoutRequest() {
  try {
    await authApiClient.post('/auth/logout')
    return;
  } catch (error) {
    throw new Error("Error al cerrar sesión")
  }
}

// ---------------- VERIFY SESSION ----------------
export async function verifySessionRequest() {
  try {
    await authApiClient.get('/auth/verify')
    return true
  } catch (error) {
    if (error.status === 401) {
      const refreshed = await refreshTokenRequest()
      if (!refreshed) return false

      try {
        await authApiClient.get('/auth/verify')
        return true
      } catch (retryError) {
        return false
      }
    }
    return false
  }
}

// ---------------- REFRESH TOKEN ----------------
export async function refreshTokenRequest() {
  try {
    await authApiClient.post('/auth/refresh')
    return true
  } catch (error) {
    return false
  }
}
