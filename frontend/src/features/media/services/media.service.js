import { API_CONFIG } from '../../../config/constants.js'
import { getAccessToken } from '../../auth/services/auth-token.js'
import { apiClient } from '../../../lib/api-client.js'

export const MAX_IMAGE_BYTES = 10 * 1024 * 1024
export const MAX_IMAGE_MB = MAX_IMAGE_BYTES / (1024 * 1024)

/** Same values as the backend MediaPurpose enum (T17a). */
export const MEDIA_PURPOSE = {
  AVATAR: 'AVATAR',
  COMMUNITY_COVER: 'COMMUNITY_COVER',
  GROUP_IMAGE: 'GROUP_IMAGE',
  EVENT_IMAGE: 'EVENT_IMAGE',
  POST_IMAGE: 'POST_IMAGE',
}

/** Client-side checks, so an obviously wrong file never hits the API. Returns an error message or null. */
export function validateImageFile(file) {
  if (!file) return 'No se ha seleccionado ningún archivo'
  if (!file.type || !file.type.startsWith('image/')) return 'El archivo no es una imagen'
  if (file.size > MAX_IMAGE_BYTES) return `La imagen es demasiado grande (máximo ${MAX_IMAGE_MB} MB)`
  return null
}

function errorFrom(xhr) {
  let detail = null
  try {
    detail = JSON.parse(xhr.responseText)?.detail || null
  } catch {
    // not JSON: use the generic message of the status
  }
  let message
  if (xhr.status === 413) message = `La imagen es demasiado grande (máximo ${MAX_IMAGE_MB} MB)`
  else if (xhr.status >= 500) message = 'Error del servidor. Inténtalo de nuevo más tarde.'
  else if (xhr.status === 401) message = 'Tu sesión ha caducado. Vuelve a iniciar sesión.'
  else message = detail || 'No se pudo subir la imagen'
  const error = new Error(message)
  error.status = xhr.status
  return error
}

function send(file, purpose, { onProgress, signal } = {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_CONFIG.BASE_URL}/api/media`)
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    const token = getAccessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    xhr.upload.onprogress = (event) => {
      if (onProgress && event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText))
        } catch {
          reject(new Error('Respuesta no válida del servidor'))
        }
      } else {
        reject(errorFrom(xhr))
      }
    }
    xhr.onerror = () => reject(new Error('No se pudo subir la imagen. Revisa tu conexión e inténtalo de nuevo.'))
    xhr.ontimeout = xhr.onerror
    xhr.onabort = () => reject(Object.assign(new Error('Subida cancelada'), { name: 'AbortError' }))
    signal?.addEventListener('abort', () => xhr.abort(), { once: true })

    const form = new FormData()
    form.append('file', file)
    form.append('purpose', purpose)
    xhr.send(form)
  })
}

/**
 * Uploads an image to POST /api/media (multipart; XMLHttpRequest because fetch gives no upload progress).
 * Resolves to `{ id, urls: { thumb, medium, large } }`. If the access token expired it renews it once and retries.
 */
export async function uploadImage(file, purpose, options = {}) {
  const invalid = validateImageFile(file)
  if (invalid) throw new Error(invalid)
  try {
    return await send(file, purpose, options)
  } catch (error) {
    if (error.status === 401 && (await apiClient.refreshSession().catch(() => false))) {
      return send(file, purpose, options)
    }
    throw error
  }
}
