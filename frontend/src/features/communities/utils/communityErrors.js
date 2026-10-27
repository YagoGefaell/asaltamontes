/** Human wait ("2 horas", "1 minuto") from the seconds of a Retry-After header. */
export function formatWait(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return null
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`
  if (seconds < 60) return plural(Math.ceil(seconds), 'segundo', 'segundos')
  if (seconds < 3600) return plural(Math.ceil(seconds / 60), 'minuto', 'minutos')
  if (seconds < 86400) return plural(Math.ceil(seconds / 3600), 'hora', 'horas')
  return plural(Math.ceil(seconds / 86400), 'día', 'días')
}

/** Clear Spanish message for the errors of the community screens (T19b): 403, 404, 429 and the rest. */
export function communityErrorMessage(error, fallback = 'No se pudo completar la acción.') {
  const status = error?.status
  if (status === 429) {
    const wait = formatWait(error.retryAfter)
    return wait
      ? `Has hecho esta solicitud hace poco. Podrás volver a intentarlo en ${wait}.`
      : 'Has hecho esta solicitud hace poco. Inténtalo de nuevo más tarde.'
  }
  if (status === 403) return error.message || 'No tienes permiso para hacer esto.'
  if (status === 404) return 'Esto ya no existe o no tienes permiso para gestionarlo. Recarga la página.'
  if (status === 401) return 'Tu sesión ha caducado. Vuelve a iniciar sesión.'
  if (error?.code === 'NETWORK_ERROR' || error?.code === 'TIMEOUT') return 'Sin conexión con el servidor. Inténtalo de nuevo.'
  return error?.message || fallback
}

/** Staff = owner or moderator. */
export const isStaff = (role) => role === 'OWNER' || role === 'MODERATOR'
