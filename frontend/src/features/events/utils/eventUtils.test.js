import { describe, expect, it } from 'vitest'
import { dateRange, hasActiveFilters, EMPTY_FILTERS } from './eventFilters.js'
import { directionsUrl, formatDateBadge, formatDistance, formatEventDateTime, formatPlaces, isFull } from './eventFormat.js'

describe('formatting', () => {
  it('formats the date in Spanish in the event zone', () => {
    // 07:30 UTC is 09:30 in Madrid in October (CEST)
    const text = formatEventDateTime('2026-10-24T07:30:00Z', 'Europe/Madrid')
    expect(text).toContain('sábado')
    expect(text).toContain('24 de octubre')
    expect(text).toContain('09:30')
  })

  it('shows the zone only when it differs from the browser', () => {
    const own = Intl.DateTimeFormat().resolvedOptions().timeZone
    expect(formatEventDateTime('2026-10-24T07:30:00Z', own)).not.toContain('(')
    const other = own === 'Asia/Tokyo' ? 'America/Lima' : 'Asia/Tokyo'
    expect(formatEventDateTime('2026-10-24T07:30:00Z', other)).toContain(`(${other})`)
  })

  it('does not break with an unknown zone', () => {
    expect(() => formatEventDateTime('2026-10-24T07:30:00Z', 'Nope/Nowhere')).not.toThrow()
  })

  it('builds the date badge', () => {
    expect(formatDateBadge('2026-10-24T07:30:00Z', 'Europe/Madrid')).toEqual({ day: '24', month: 'oct' })
  })

  it('formats distance, places and fullness', () => {
    expect(formatDistance(12.5, 800)).toBe('12,5 km · 800 m+')
    expect(formatDistance(null, null)).toBe('')
    expect(formatPlaces(7, 12)).toBe('7/12')
    expect(formatPlaces(3, null)).toBe('3 apuntadas')
    expect(isFull({ capacity: 2, attendeeCount: 2 })).toBe(true)
    expect(isFull({ capacity: null, attendeeCount: 99 })).toBe(false)
  })

  it('links to the directions with lat/lng', () => {
    expect(directionsUrl(40.4, -3.7)).toContain('destination=40.4,-3.7')
  })
})

describe('date presets', () => {
  const at = (iso) => new Date(iso)

  it('"hoy" ends at the next local midnight', () => {
    const now = at('2026-10-22T10:00:00')
    const { from, to } = dateRange('today', now)
    expect(from).toBe(now.toISOString())
    expect(to).toBe(at('2026-10-23T00:00:00').toISOString())
  })

  it('"este finde" from a weekday is Saturday 00:00 to Monday 00:00', () => {
    const { from, to } = dateRange('weekend', at('2026-10-22T10:00:00')) // Thursday
    expect(from).toBe(at('2026-10-24T00:00:00').toISOString())
    expect(to).toBe(at('2026-10-26T00:00:00').toISOString())
  })

  it('"este finde" on Sunday starts now and ends on Monday', () => {
    const now = at('2026-10-25T11:00:00') // Sunday
    const { from, to } = dateRange('weekend', now)
    expect(from).toBe(now.toISOString())
    expect(to).toBe(at('2026-10-26T00:00:00').toISOString())
  })

  it('"30 días" and the default', () => {
    const now = at('2026-10-22T10:00:00')
    expect(dateRange('30d', now).to).toBe(at('2026-11-21T10:00:00').toISOString())
    expect(dateRange('all', now)).toEqual({})
  })

  it('detects active filters', () => {
    expect(hasActiveFilters(EMPTY_FILTERS)).toBe(false)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, mine: true })).toBe(true)
    expect(hasActiveFilters({ ...EMPTY_FILTERS, city: '  ' })).toBe(false)
  })
})
