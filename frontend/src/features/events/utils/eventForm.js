// Event form: initial values, validation mirroring the API (CreateEventDto / UpdateEventDto) and request payloads.
// Dates are typed as local wall time ("2026-11-02T09:30") and mean that time in the event's timezone.

export const TITLE_MAX = 120
export const DESCRIPTION_MAX = 4000
export const MEETING_POINT_MAX = 200
export const CITY_MAX = 80

export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone

export const EMPTY_FORM = {
  title: '',
  description: '',
  type: 'MEETUP',
  startsAt: '',
  endsAt: '',
  meetingPointName: '',
  latitude: null,
  longitude: null,
  city: '',
  distanceKm: '',
  elevationGainM: '',
  level: '',
  capacity: '',
  visibility: 'PUBLIC',
  communityId: '',
}

// ---------- timezones ----------

// Offset (ms) of `timeZone` at the instant `ts`: wall clock minus UTC
function zoneOffset(ts, timeZone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(new Date(ts))
  const get = (type) => Number(parts.find((p) => p.type === type).value)
  const wall = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'))
  return wall - Math.floor(ts / 1000) * 1000
}

const LOCAL_RE = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/

/** "2026-11-02T09:30" as a wall time in `timeZone` -> ISO instant; null if the text or the zone are not valid. */
export function zonedLocalToIso(local, timeZone = browserTimeZone()) {
  const m = LOCAL_RE.exec(local || '')
  if (!m) return null
  const [y, mo, d, h, mi] = m.slice(1).map(Number)
  const naive = Date.UTC(y, mo - 1, d, h, mi)
  if (new Date(naive).getUTCDate() !== d) return null // 31 Feb and the like
  try {
    let ts = naive - zoneOffset(naive, timeZone)
    // Around a DST change the first guess can be off by the shift: recompute with the offset at the result
    ts = naive - zoneOffset(ts, timeZone)
    return new Date(ts).toISOString()
  } catch {
    return null
  }
}

/** ISO instant -> "2026-11-02T09:30" wall time in `timeZone` (for datetime-local inputs). */
export function isoToZonedLocal(iso, timeZone = browserTimeZone()) {
  if (!iso) return ''
  const ts = new Date(iso).getTime()
  if (Number.isNaN(ts)) return ''
  let offset
  try {
    offset = zoneOffset(ts, timeZone)
  } catch {
    offset = zoneOffset(ts, browserTimeZone())
  }
  return new Date(ts + offset).toISOString().slice(0, 16)
}

// ---------- values <-> event ----------

/** Form values from an existing event (edit mode), with the dates in the event's own timezone. */
export function formFromEvent(event) {
  const zone = event.timezone || browserTimeZone()
  return {
    title: event.title ?? '',
    description: event.description ?? '',
    type: event.type ?? 'MEETUP',
    startsAt: isoToZonedLocal(event.startsAt, zone),
    endsAt: isoToZonedLocal(event.endsAt, zone),
    meetingPointName: event.meetingPointName ?? '',
    latitude: event.latitude ?? null,
    longitude: event.longitude ?? null,
    city: event.city ?? '',
    distanceKm: event.distanceKm != null ? String(event.distanceKm) : '',
    elevationGainM: event.elevationGainM != null ? String(event.elevationGainM) : '',
    level: event.level ?? '',
    capacity: event.capacity != null ? String(event.capacity) : '',
    visibility: event.visibility ?? 'PUBLIC',
    communityId: event.communityId != null ? String(event.communityId) : '',
  }
}

// ---------- validation ----------

const toNumber = (text) => (String(text).trim() === '' ? null : Number(String(text).replace(',', '.')))

/**
 * Field errors (empty object = valid). `timeZone` is the event's; `original` is set when editing:
 * the API only demands a future start when the start changes.
 */
export function validateEventForm(values, { timeZone = browserTimeZone(), now = new Date(), original = null } = {}) {
  const errors = {}
  const title = values.title.trim()
  if (!title) errors.title = 'El título es obligatorio'
  else if (title.length > TITLE_MAX) errors.title = `El título no puede superar los ${TITLE_MAX} caracteres`

  if (values.description.length > DESCRIPTION_MAX) errors.description = `La descripción no puede superar los ${DESCRIPTION_MAX} caracteres`
  if (!values.type) errors.type = 'El tipo de evento es obligatorio'

  const startsIso = zonedLocalToIso(values.startsAt, timeZone)
  if (!values.startsAt) errors.startsAt = 'La fecha de inicio es obligatoria'
  else if (!startsIso) errors.startsAt = 'Fecha de inicio no válida'
  else if (new Date(startsIso) <= now && (!original || values.startsAt !== original.startsAt)) {
    errors.startsAt = 'El evento tiene que empezar en el futuro'
  }

  if (values.endsAt) {
    const endsIso = zonedLocalToIso(values.endsAt, timeZone)
    if (!endsIso) errors.endsAt = 'Fecha de fin no válida'
    else if (startsIso && new Date(endsIso) <= new Date(startsIso)) errors.endsAt = 'El evento tiene que terminar después de empezar'
  }

  if (values.meetingPointName.length > MEETING_POINT_MAX) errors.meetingPointName = `El punto de encuentro no puede superar los ${MEETING_POINT_MAX} caracteres`
  if (values.city.length > CITY_MAX) errors.city = `La ciudad no puede superar los ${CITY_MAX} caracteres`

  // The point is set through the map, so both or none always hold; the check mirrors the API anyway
  if ((values.latitude == null) !== (values.longitude == null)) errors.location = 'Indica latitud y longitud, o ninguna de las dos'
  else if (values.latitude != null && (values.latitude < -90 || values.latitude > 90 || values.longitude < -180 || values.longitude > 180)) {
    errors.location = 'Ubicación no válida'
  }

  const distance = toNumber(values.distanceKm)
  if (distance != null && (Number.isNaN(distance) || distance < 0.1)) errors.distanceKm = 'La distancia debe ser positiva'
  else if (distance != null && distance > 9999.9) errors.distanceKm = 'Distancia no válida'

  const elevation = toNumber(values.elevationGainM)
  if (elevation != null && (!Number.isInteger(elevation) || elevation < 0)) errors.elevationGainM = 'El desnivel no puede ser negativo'
  else if (elevation != null && elevation > 20000) errors.elevationGainM = 'Desnivel no válido'

  const capacity = toNumber(values.capacity)
  if (capacity != null && (!Number.isInteger(capacity) || capacity < 1)) errors.capacity = 'Debe haber al menos una plaza'
  else if (capacity != null && capacity > 500) errors.capacity = 'Como máximo 500 plazas'

  if (values.visibility === 'COMMUNITY' && !values.communityId) errors.communityId = 'Un evento solo para miembros necesita una comunidad'
  return errors
}

// ---------- payloads ----------

const orNull = (text) => (text.trim() === '' ? null : text.trim())

/** Body of POST /api/events. Empty optional fields are left out. */
export function toCreatePayload(values, timeZone = browserTimeZone()) {
  const payload = {
    title: values.title.trim(),
    type: values.type,
    startsAt: zonedLocalToIso(values.startsAt, timeZone),
    timezone: timeZone,
    visibility: values.communityId ? values.visibility : 'PUBLIC',
  }
  const optional = {
    description: orNull(values.description),
    endsAt: values.endsAt ? zonedLocalToIso(values.endsAt, timeZone) : null,
    meetingPointName: orNull(values.meetingPointName),
    latitude: values.latitude,
    longitude: values.longitude,
    city: orNull(values.city),
    distanceKm: toNumber(values.distanceKm),
    elevationGainM: toNumber(values.elevationGainM),
    level: values.level || null,
    capacity: toNumber(values.capacity),
    communityId: values.communityId ? Number(values.communityId) : null,
  }
  for (const [key, value] of Object.entries(optional)) if (value != null) payload[key] = value
  return payload
}

/**
 * Body of PATCH /api/events/{id}: only what changed (the API leaves null fields as they are).
 * Gap: the API cannot clear an optional field once set (e.g. remove the capacity), except the description.
 */
export function toUpdatePayload(values, original, timeZone) {
  const full = toCreatePayload(values, timeZone)
  const before = toCreatePayload(original, timeZone)
  const payload = {}
  for (const key of Object.keys(full)) {
    if (key === 'visibility' || key === 'communityId' || key === 'timezone') continue
    if (full[key] !== before[key]) payload[key] = full[key]
  }
  if (values.description.trim() === '' && original.description.trim() !== '') payload.description = ''
  return payload
}
