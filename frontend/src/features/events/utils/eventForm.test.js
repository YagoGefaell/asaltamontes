import { describe, expect, it } from 'vitest'
import {
  EMPTY_FORM, formFromEvent, isoToZonedLocal, toCreatePayload, toUpdatePayload, validateEventForm, zonedLocalToIso,
} from './eventForm.js'

const NOW = new Date('2026-10-22T10:00:00Z')
const ZONE = 'Europe/Madrid'
const valid = { ...EMPTY_FORM, title: 'Rodaje', startsAt: '2026-11-02T09:30' }
const check = (patch, opts = {}) => validateEventForm({ ...valid, ...patch }, { timeZone: ZONE, now: NOW, ...opts })

describe('zones', () => {
  it('reads the wall time in the zone of the event (winter UTC+1, summer UTC+2)', () => {
    expect(zonedLocalToIso('2026-11-02T09:30', ZONE)).toBe('2026-11-02T08:30:00.000Z')
    expect(zonedLocalToIso('2026-07-02T09:30', ZONE)).toBe('2026-07-02T07:30:00.000Z')
    expect(zonedLocalToIso('2026-07-02T09:30', 'America/Bogota')).toBe('2026-07-02T14:30:00.000Z')
  })

  it('round-trips and survives a DST change day', () => {
    expect(isoToZonedLocal('2026-11-02T08:30:00.000Z', ZONE)).toBe('2026-11-02T09:30')
    // 2026-10-25 02:30 happens twice in Madrid; either instant converts back to the same wall time
    const iso = zonedLocalToIso('2026-10-25T10:00', ZONE)
    expect(isoToZonedLocal(iso, ZONE)).toBe('2026-10-25T10:00')
  })

  it('rejects text that is not a date', () => {
    expect(zonedLocalToIso('', ZONE)).toBeNull()
    expect(zonedLocalToIso('2026-02-31T10:00', ZONE)).toBeNull()
    expect(zonedLocalToIso('2026-11-02T09:30', 'Not/AZone')).toBeNull()
  })
})

describe('validateEventForm (mirror of the API)', () => {
  it('accepts a minimal event', () => {
    expect(check({})).toEqual({})
  })

  it('requires title, start and type', () => {
    expect(check({ title: '   ' }).title).toBe('El título es obligatorio')
    expect(check({ startsAt: '' }).startsAt).toBe('La fecha de inicio es obligatoria')
    expect(check({ type: '' }).type).toBe('El tipo de evento es obligatorio')
  })

  it('limits lengths like the DTO', () => {
    expect(check({ title: 'x'.repeat(121) }).title).toMatch(/120/)
    expect(check({ description: 'x'.repeat(4001) }).description).toMatch(/4000/)
    expect(check({ meetingPointName: 'x'.repeat(201) }).meetingPointName).toMatch(/200/)
    expect(check({ city: 'x'.repeat(81) }).city).toMatch(/80/)
  })

  it('needs a future start, except when editing without changing it', () => {
    expect(check({ startsAt: '2026-10-22T11:00' }).startsAt).toBe('El evento tiene que empezar en el futuro')
    const original = { ...valid, startsAt: '2026-10-22T11:00' }
    expect(check({ startsAt: '2026-10-22T11:00' }, { original })).toEqual({})
    expect(check({ startsAt: '2026-10-22T11:30' }, { original }).startsAt).toBeDefined()
  })

  it('needs the end after the start', () => {
    expect(check({ endsAt: '2026-11-02T09:30' }).endsAt).toBe('El evento tiene que terminar después de empezar')
    expect(check({ endsAt: '2026-11-02T12:00' })).toEqual({})
  })

  it('checks numbers and ranges', () => {
    expect(check({ distanceKm: '0' }).distanceKm).toBeDefined()
    expect(check({ distanceKm: '10000' }).distanceKm).toBeDefined()
    expect(check({ distanceKm: '12,5' })).toEqual({})
    expect(check({ elevationGainM: '-1' }).elevationGainM).toBeDefined()
    expect(check({ elevationGainM: '20001' }).elevationGainM).toBeDefined()
    expect(check({ capacity: '0' }).capacity).toBe('Debe haber al menos una plaza')
    expect(check({ capacity: '501' }).capacity).toBe('Como máximo 500 plazas')
    expect(check({ capacity: '2.5' }).capacity).toBeDefined()
  })

  it('wants both coordinates or none', () => {
    expect(check({ latitude: 40.1, longitude: null }).location).toBeDefined()
    expect(check({ latitude: 95, longitude: 1 }).location).toBeDefined()
    expect(check({ latitude: 40.1, longitude: -3.7 })).toEqual({})
  })

  it('members-only events need a community', () => {
    expect(check({ visibility: 'COMMUNITY' }).communityId).toBeDefined()
    expect(check({ visibility: 'COMMUNITY', communityId: '4' })).toEqual({})
  })
})

describe('payloads', () => {
  it('create sends the instant, the zone and only the fields she filled', () => {
    expect(toCreatePayload(valid, ZONE)).toEqual({
      title: 'Rodaje', type: 'MEETUP', startsAt: '2026-11-02T08:30:00.000Z', timezone: ZONE, visibility: 'PUBLIC',
    })
    const full = toCreatePayload({
      ...valid, title: ' Rodaje ', description: 'Suave', endsAt: '2026-11-02T11:30', latitude: 40.1, longitude: -3.7,
      distanceKm: '12,5', elevationGainM: '300', capacity: '12', level: 'BEGINNER', communityId: '4', visibility: 'COMMUNITY',
    }, ZONE)
    expect(full).toMatchObject({
      title: 'Rodaje', description: 'Suave', endsAt: '2026-11-02T10:30:00.000Z', latitude: 40.1, longitude: -3.7,
      distanceKm: 12.5, elevationGainM: 300, capacity: 12, level: 'BEGINNER', communityId: 4, visibility: 'COMMUNITY',
    })
  })

  it('a visibility without community goes out as PUBLIC', () => {
    expect(toCreatePayload({ ...valid, visibility: 'COMMUNITY' }, ZONE).visibility).toBe('PUBLIC')
  })

  it('edit sends only what changed, and never community or visibility', () => {
    const event = {
      title: 'Rodaje', type: 'MEETUP', startsAt: '2026-11-02T08:30:00Z', timezone: ZONE, visibility: 'COMMUNITY', communityId: 4,
      capacity: 10, description: 'Suave',
    }
    const original = formFromEvent(event)
    expect(original.startsAt).toBe('2026-11-02T09:30')
    expect(toUpdatePayload(original, original, ZONE)).toEqual({})
    expect(toUpdatePayload({ ...original, capacity: '12', title: 'Rodaje largo' }, original, ZONE))
      .toEqual({ capacity: 12, title: 'Rodaje largo' })
  })

  it('create sends the zone chosen in the form, not an assumed one', () => {
    expect(toCreatePayload({ ...valid, timezone: 'America/Bogota' }).timezone).toBe('America/Bogota')
    expect(toCreatePayload({ ...valid, timezone: 'America/Bogota' }).startsAt).toBe('2026-11-02T14:30:00.000Z')
    expect(EMPTY_FORM.timezone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
  })

  it('edit sends null for every optional field she emptied (D-020)', () => {
    const event = {
      title: 'Rodaje', type: 'MEETUP', startsAt: '2026-11-02T08:30:00Z', endsAt: '2026-11-02T10:30:00Z', timezone: ZONE,
      description: 'Suave', meetingPointName: 'Parking', latitude: 40.1, longitude: -3.7, city: 'Madrid',
      distanceKm: 12.5, elevationGainM: 300, level: 'BEGINNER', capacity: 10, visibility: 'PUBLIC',
    }
    const original = formFromEvent(event)
    const emptied = {
      ...original, description: '  ', endsAt: '', meetingPointName: '', latitude: null, longitude: null, city: '',
      distanceKm: '', elevationGainM: '', level: '', capacity: '',
    }
    expect(toUpdatePayload(emptied, original)).toEqual({
      description: null, endsAt: null, meetingPointName: null, latitude: null, longitude: null, city: null,
      distanceKm: null, elevationGainM: null, level: null, capacity: null,
    })
    // Untouched fields are absent, never null
    expect(toUpdatePayload({ ...original, city: 'Vigo' }, original)).toEqual({ city: 'Vigo' })
  })

  it('edit: changing only the zone sends it with the instants re-read in the new zone', () => {
    const original = formFromEvent({ title: 'R', type: 'MEETUP', startsAt: '2026-11-02T08:30:00Z', timezone: ZONE })
    expect(toUpdatePayload({ ...original, timezone: 'America/Bogota' }, original)).toEqual({
      timezone: 'America/Bogota', startsAt: '2026-11-02T14:30:00.000Z',
    })
    // A zone change counts as a change of start: it must be in the future
    expect(validateEventForm({ ...original, timezone: 'America/Bogota' }, { now: new Date('2027-01-01T00:00:00Z'), original }).startsAt)
      .toBeDefined()
  })
})
