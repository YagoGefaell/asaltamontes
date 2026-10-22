export const EVENT_TYPES = {
  MEETUP: 'Quedada',
  TRAINING: 'Entrenamiento',
  RACE: 'Carrera',
  TRIP: 'Excursión',
}

export const EVENT_LEVELS = {
  BEGINNER: 'Principiante',
  INTERMEDIATE: 'Intermedio',
  ADVANCED: 'Avanzado',
  EXPERT: 'Experta',
}

const browserZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

function formatIn(date, timeZone, options) {
  const opts = { ...options }
  if (timeZone) opts.timeZone = timeZone
  try {
    return new Intl.DateTimeFormat('es-ES', opts).format(date)
  } catch {
    // Unknown timezone id: fall back to the browser's
    return new Intl.DateTimeFormat('es-ES', options).format(date)
  }
}

/** Day and month for the big date badge of the card: { day: '25', month: 'oct' } */
export function formatDateBadge(iso, timeZone) {
  const date = new Date(iso)
  return {
    day: formatIn(date, timeZone, { day: 'numeric' }),
    month: formatIn(date, timeZone, { month: 'short' }).replace('.', ''),
  }
}

/** "sábado, 25 de octubre · 09:30"; adds the zone when the event's differs from the browser's. */
export function formatEventDateTime(iso, timeZone) {
  const date = new Date(iso)
  const day = formatIn(date, timeZone, { weekday: 'long', day: 'numeric', month: 'long' })
  const time = formatIn(date, timeZone, { hour: '2-digit', minute: '2-digit' })
  let text = `${day} · ${time}`
  if (timeZone && timeZone !== browserZone()) {
    text += ` (${timeZone.replace(/_/g, ' ')})`
  }
  return text
}

export function formatDistance(distanceKm, elevationGainM) {
  const parts = []
  if (distanceKm != null) parts.push(`${Number(distanceKm).toLocaleString('es-ES', { maximumFractionDigits: 1 })} km`)
  if (elevationGainM != null) parts.push(`${elevationGainM} m+`)
  return parts.join(' · ')
}

export function formatPlaces(attendeeCount, capacity) {
  return capacity == null ? `${attendeeCount} apuntadas` : `${attendeeCount}/${capacity}`
}

export function isFull(event) {
  return event.capacity != null && event.attendeeCount >= event.capacity
}

/** Directions link: opens Google Maps, or the native maps app on phones. */
export function directionsUrl(latitude, longitude) {
  return `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`
}
