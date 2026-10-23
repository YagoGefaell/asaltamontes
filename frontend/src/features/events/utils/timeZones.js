// IANA time zones for the event form (D-019). The API only accepts region ids (Europe/Madrid), never fixed offsets.

// Fallback for engines without Intl.supportedValuesOf: the main zones of every region, not only Spain's
const FALLBACK_ZONES = [
  'UTC', 'Africa/Cairo', 'Africa/Johannesburg', 'Africa/Lagos', 'Africa/Nairobi', 'Africa/Casablanca',
  'America/Anchorage', 'America/Argentina/Buenos_Aires', 'America/Bogota', 'America/Chicago', 'America/Denver',
  'America/Halifax', 'America/Lima', 'America/Los_Angeles', 'America/Mexico_City', 'America/New_York',
  'America/Santiago', 'America/Sao_Paulo', 'America/Toronto', 'America/Vancouver',
  'Asia/Bangkok', 'Asia/Dubai', 'Asia/Hong_Kong', 'Asia/Jakarta', 'Asia/Kathmandu', 'Asia/Kolkata', 'Asia/Seoul',
  'Asia/Shanghai', 'Asia/Singapore', 'Asia/Tokyo',
  'Atlantic/Azores', 'Atlantic/Canary', 'Atlantic/Reykjavik',
  'Australia/Melbourne', 'Australia/Perth', 'Australia/Sydney',
  'Europe/Amsterdam', 'Europe/Athens', 'Europe/Berlin', 'Europe/Dublin', 'Europe/Istanbul', 'Europe/Lisbon',
  'Europe/London', 'Europe/Madrid', 'Europe/Moscow', 'Europe/Paris', 'Europe/Rome', 'Europe/Zurich',
  'Pacific/Auckland', 'Pacific/Honolulu', 'Pacific/Fiji',
]

/** All zones the browser knows (sorted), with UTC, and always including `current` so the select can show it. */
export function listTimeZones(current) {
  let zones
  try {
    zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : FALLBACK_ZONES
  } catch {
    zones = FALLBACK_ZONES
  }
  const set = new Set(zones)
  set.add('UTC')
  if (current) set.add(current)
  return [...set].sort((a, b) => a.localeCompare(b))
}

const normalize = (text) => text.toLowerCase().replace(/[\s_/-]+/g, ' ').trim()

/** Zones matching what she types ("new york", "buenos", "asia"). `keep` stays in the list even if it does not match. */
export function filterTimeZones(zones, query, keep) {
  const q = normalize(query || '')
  if (!q) return zones
  return zones.filter((zone) => zone === keep || normalize(zone).includes(q))
}

/** "America/Argentina/Buenos_Aires" -> "America/Argentina/Buenos Aires" */
export const zoneLabel = (zone) => zone.replace(/_/g, ' ')
