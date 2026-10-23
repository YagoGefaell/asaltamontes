import { describe, expect, it } from 'vitest'
import { filterTimeZones, listTimeZones, zoneLabel } from './timeZones.js'

describe('time zones', () => {
  it('lists IANA region ids, always with UTC and the current value', () => {
    const zones = listTimeZones('Pacific/Chatham')
    expect(zones).toContain('Europe/Madrid')
    expect(zones).toContain('UTC')
    expect(zones).toContain('Pacific/Chatham')
    expect(zones.some((z) => /^[+-]\d/.test(z))).toBe(false)
  })

  it('filters by what she types, ignoring case, underscores and slashes', () => {
    const zones = ['America/Argentina/Buenos_Aires', 'Asia/Tokyo', 'Europe/Madrid']
    expect(filterTimeZones(zones, 'buenos aires')).toEqual(['America/Argentina/Buenos_Aires'])
    expect(filterTimeZones(zones, 'ASIA')).toEqual(['Asia/Tokyo'])
    expect(filterTimeZones(zones, '')).toEqual(zones)
    expect(filterTimeZones(zones, 'zzz')).toEqual([])
  })

  it('keeps the selected zone visible even if it does not match the search', () => {
    expect(filterTimeZones(['Asia/Tokyo', 'Europe/Madrid'], 'tokyo', 'Europe/Madrid')).toEqual(['Asia/Tokyo', 'Europe/Madrid'])
  })

  it('labels with spaces', () => {
    expect(zoneLabel('America/Argentina/Buenos_Aires')).toBe('America/Argentina/Buenos Aires')
  })
})
