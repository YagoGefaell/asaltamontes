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

// Dates and numbers follow the locale of the browser (no Spain assumptions, D-019); `locale` is for tests
const browserLocale = () => (typeof navigator !== 'undefined' && navigator.languages?.[0]) || undefined

function formatIn(date, timeZone, options, locale = browserLocale()) {
  const opts = { ...options }
  if (timeZone) opts.timeZone = timeZone
  try {
    return new Intl.DateTimeFormat(locale, opts).format(date)
  } catch {
    // Unknown timezone id: fall back to the browser's
    return new Intl.DateTimeFormat(locale, options).format(date)
  }
}

/** Day and month for the big date badge of the card: { day: '25', month: 'oct' } */
export function formatDateBadge(iso, timeZone, locale) {
  const date = new Date(iso)
  return {
    day: formatIn(date, timeZone, { day: 'numeric' }, locale),
    month: formatIn(date, timeZone, { month: 'short' }, locale).replace('.', ''),
  }
}

/** "sábado, 25 de octubre · 09:30" at the event's place; adds the zone when it differs from the viewer's. */
export function formatEventDateTime(iso, timeZone, locale, viewerZone = browserZone()) {
  const date = new Date(iso)
  const day = formatIn(date, timeZone, { weekday: 'long', day: 'numeric', month: 'long' }, locale)
  const time = formatIn(date, timeZone, { hour: '2-digit', minute: '2-digit' }, locale)
  let text = `${day} · ${time}`
  if (timeZone && timeZone !== viewerZone) {
    text += ` (${timeZone.replace(/_/g, ' ')})`
  }
  return text
}

/** The same moment in the viewer's own zone, or '' when it is the event's zone (nothing to add). */
export function formatViewerDateTime(iso, timeZone, locale, viewerZone = browserZone()) {
  if (!timeZone || timeZone === viewerZone) return ''
  const date = new Date(iso)
  const day = formatIn(date, viewerZone, { weekday: 'short', day: 'numeric', month: 'short' }, locale)
  const time = formatIn(date, viewerZone, { hour: '2-digit', minute: '2-digit' }, locale)
  return `${day} · ${time}`
}

// Distances are stored in SI (km, m) and only formatted here, with the viewer's number format
export function formatDistance(distanceKm, elevationGainM, locale = browserLocale()) {
  const parts = []
  if (distanceKm != null) parts.push(`${Number(distanceKm).toLocaleString(locale, { maximumFractionDigits: 1 })} km`)
  if (elevationGainM != null) parts.push(`${Number(elevationGainM).toLocaleString(locale)} m+`)
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
