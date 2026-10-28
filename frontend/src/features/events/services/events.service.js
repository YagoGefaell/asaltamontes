import { apiClient } from '../../../lib/api-client.js'

export const EVENTS_PAGE_SIZE = 20
// Keeps the API's field errors (error.errors) so the form can show them next to each field
function apiError(error, fallback) {
  const wrapped = new Error(error.message || fallback)
  wrapped.status = error.status
  wrapped.errors = error.errors
  return wrapped
}

function toPage(response, fallbackPage) {
  return {
    items: Array.isArray(response?.content) ? response.content : [],
    hasMore: response?.last === false,
    page: Number.isInteger(response?.number) ? response.number : fallbackPage,
  }
}

// Query string of GET /api/events; only the filters that have a value are sent (T15c)
export function buildEventsQuery({ page = 0, from, to, type, level, city, mine, followedBy, bounds, size = EVENTS_PAGE_SIZE } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (type) params.set('type', type)
  if (level) params.set('level', level)
  if (city && city.trim()) params.set('city', city.trim())
  if (mine) params.set('attending', 'me')
  if (followedBy) params.set('followedBy', 'me')
  if (bounds) {
    params.set('minLat', String(bounds.minLat))
    params.set('maxLat', String(bounds.maxLat))
    params.set('minLng', String(bounds.minLng))
    params.set('maxLng', String(bounds.maxLng))
  }
  return params
}

// The API rejects boxes outside the world, and a zoomed-out or wrapped map can report them
const clamp = (n, min, max) => Math.min(max, Math.max(min, n))
const round = (n) => Math.round(n * 1e5) / 1e5

/** Bounding box of the API from the corners of the visible map area. */
export function toBounds({ south, west, north, east }) {
  return {
    minLat: round(clamp(south, -90, 90)),
    maxLat: round(clamp(north, -90, 90)),
    minLng: round(clamp(west, -180, 180)),
    maxLng: round(clamp(east, -180, 180)),
  }
}

// -------- SEARCH EVENTS --------
export async function getEventsRequest(filters = {}) {
  try {
    const response = await apiClient.get(`/api/events?${buildEventsQuery(filters)}`)
    return toPage(response, filters.page ?? 0)
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo quedadas')
  }
}

// -------- EVENTS ANOTHER USER ORGANIZES (T31b) --------
// Only what she organizes and the caller can see; never her attendance
export async function getUserEventsRequest(username, { page = 0, size = EVENTS_PAGE_SIZE, from } = {}) {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (from) params.set('from', from)
    const response = await apiClient.get(`/api/users/${encodeURIComponent(username)}/events?${params}`)
    return toPage(response, page)
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo sus quedadas')
  }
}

// Pins per request: the map shows the soonest ones of the area and asks to zoom in for the rest
export const MAP_EVENTS_LIMIT = 50

// -------- SEARCH EVENTS IN AN AREA (map view) --------
// One request, same filters as the list. `total` is how many exist in the area (more than `items` when capped).
export async function getEventsInBoundsRequest(bounds, filters = {}) {
  try {
    const params = buildEventsQuery({ ...filters, bounds, page: 0, size: MAP_EVENTS_LIMIT })
    const response = await apiClient.get(`/api/events?${params}`)
    const items = Array.isArray(response?.content) ? response.content : []
    return { items, total: Number.isInteger(response?.totalElements) ? response.totalElements : items.length }
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo quedadas del mapa')
  }
}

// -------- GET EVENT --------
export async function getEventRequest(eventId) {
  try {
    return await apiClient.get(`/api/events/${encodeURIComponent(eventId)}`)
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo la quedada')
  }
}

// -------- JOIN / LEAVE --------
// Both return the updated event (myStatus GOING / WAITLIST / CANCELLED)
export async function joinEventRequest(eventId) {
  try {
    return await apiClient.post(`/api/events/${encodeURIComponent(eventId)}/join`, {})
  } catch (error) {
    throw new Error(error.message || 'Error al apuntarte a la quedada')
  }
}

export async function leaveEventRequest(eventId) {
  try {
    return await apiClient.post(`/api/events/${encodeURIComponent(eventId)}/leave`, {})
  } catch (error) {
    throw new Error(error.message || 'Error al salir de la quedada')
  }
}

// -------- ATTENDEES --------
export async function getEventAttendeesRequest(eventId, { status = 'GOING', page = 0, size = EVENTS_PAGE_SIZE } = {}) {
  try {
    const params = new URLSearchParams({ status, page: String(page), size: String(size) })
    const response = await apiClient.get(`/api/events/${encodeURIComponent(eventId)}/attendees?${params}`)
    return toPage(response, page)
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo asistentes')
  }
}

// -------- CREATE / EDIT --------
export async function createEventRequest(payload) {
  try {
    return await apiClient.post('/api/events', payload)
  } catch (error) {
    throw apiError(error, 'Error creando la quedada')
  }
}

export async function updateEventRequest(eventId, payload) {
  try {
    return await apiClient.patch(`/api/events/${encodeURIComponent(eventId)}`, payload)
  } catch (error) {
    throw apiError(error, 'Error guardando la quedada')
  }
}

// -------- CHAT (T21) --------
// { conversationId, canWrite, readOnlyReason } for someone who is going (readOnlyReason: null | CANCELLED | ARCHIVED);
// 404 for anyone else, the waitlist included
export async function getEventChatRequest(eventId) {
  try {
    return await apiClient.get(`/api/events/${encodeURIComponent(eventId)}/chat`)
  } catch (error) {
    throw new Error(error.message || 'Error abriendo el chat de la quedada')
  }
}

// -------- CALENDAR (T21) --------
// The iCalendar file as text. It needs the token, so it is fetched (not a plain link) and saved as a file.
export async function getEventCalendarRequest(eventId) {
  try {
    return await apiClient.get(`/api/events/${encodeURIComponent(eventId)}.ics`, { headers: { Accept: 'text/calendar' } })
  } catch (error) {
    throw new Error(error.message || 'Error descargando el calendario')
  }
}

// -------- COMMUNITY EVENTS --------
// Upcoming events of a community the user can see
export async function getCommunityEventsRequest(slug, { page = 0, size = 5 } = {}) {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    const response = await apiClient.get(`/api/communities/${encodeURIComponent(slug)}/events?${params}`)
    return toPage(response, page)
  } catch (error) {
    throw new Error(error.message || 'Error obteniendo las quedadas de la comunidad')
  }
}
