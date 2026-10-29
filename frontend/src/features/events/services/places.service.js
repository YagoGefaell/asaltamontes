// Place search for the meeting point (D-024): Photon, a geocoder over OpenStreetMap data, called from the browser.
// No key; komoot's public instance is fine for the demo, with real traffic it must be self-hosted or replaced.

export const PHOTON_URL = 'https://photon.komoot.io/api/'
export const PLACES_LIMIT = 6

const join = (...parts) => parts.filter(Boolean).join(' ')

/** One Photon GeoJSON feature -> { id, name, detail, city, latitude, longitude }. */
export function toPlace(feature) {
  const p = feature.properties || {}
  const [longitude, latitude] = feature.geometry?.coordinates || []
  const street = join(p.street, p.housenumber)
  const name = p.name || street || p.city || ''
  // A city found by itself is its own city; anything else belongs to the city (or the county, in the countryside)
  const city = p.type === 'city' ? p.name : p.city || p.district || p.county || ''
  const detail = [...new Set([street !== name && street, city !== name && city, p.state, p.country].filter(Boolean))].join(', ')
  return { id: `${p.osm_type}${p.osm_id}`, name, detail, city: city || '', latitude, longitude }
}

/**
 * Places matching `query`, ranked near `near` ({ latitude, longitude }) when given.
 * `signal` aborts a search the user has already typed past.
 */
export async function searchPlaces(query, { near = null, signal } = {}) {
  const params = new URLSearchParams({ q: query, limit: String(PLACES_LIMIT) })
  if (near) {
    params.set('lat', String(near.latitude))
    params.set('lon', String(near.longitude))
  }
  const response = await fetch(`${PHOTON_URL}?${params}`, { signal })
  if (!response.ok) throw new Error('No se pudo buscar el lugar')
  const data = await response.json()
  return (data.features || [])
    .map(toPlace)
    .filter((place) => place.name && Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
}

// One lookup per city and page load: her profile city doesn't change while she fills the form
const cityCache = new Map()

/** Centre ({ latitude, longitude }) of the city called `name`, or null if Photon doesn't know it. */
export async function locateCity(name, { signal } = {}) {
  const key = name.trim().toLowerCase()
  if (cityCache.has(key)) return cityCache.get(key)
  const params = new URLSearchParams({ q: name.trim(), limit: '1', layer: 'city' })
  const response = await fetch(`${PHOTON_URL}?${params}`, { signal })
  if (!response.ok) throw new Error('No se pudo localizar la ciudad')
  const [feature] = (await response.json()).features || []
  const [longitude, latitude] = feature?.geometry?.coordinates || []
  const centre = Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude } : null
  cityCache.set(key, centre)
  return centre
}
