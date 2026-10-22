import { apiClient } from '../../../lib/api-client.js'

export const EVENTS_PAGE_SIZE = 20

function toPage(response, fallbackPage) {
  return {
    items: Array.isArray(response?.content) ? response.content : [],
    hasMore: response?.last === false,
    page: Number.isInteger(response?.number) ? response.number : fallbackPage,
  }
}

// Query string of GET /api/events; only the filters that have a value are sent (T15c)
export function buildEventsQuery({ page = 0, from, to, type, level, city, mine, size = EVENTS_PAGE_SIZE } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (type) params.set('type', type)
  if (level) params.set('level', level)
  if (city && city.trim()) params.set('city', city.trim())
  if (mine) params.set('attending', 'me')
  return params
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
