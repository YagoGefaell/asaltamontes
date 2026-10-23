import { describe, expect, it } from 'vitest'
import { formatDayLabel, formatMessageTime, isSameDay, parseMessageDate } from './messageDates.js'

describe('messageDates', () => {
  it('treats strings without offset as UTC', () => {
    expect(parseMessageDate('2026-10-23T10:00:00').toISOString()).toBe('2026-10-23T10:00:00.000Z')
    expect(parseMessageDate('2026-10-23T10:00:00+02:00').toISOString()).toBe('2026-10-23T08:00:00.000Z')
  })

  it('returns null or empty for invalid dates', () => {
    expect(parseMessageDate('nope')).toBeNull()
    expect(parseMessageDate(null)).toBeNull()
    expect(formatMessageTime('nope')).toBe('')
    expect(formatDayLabel(undefined)).toBe('')
  })

  it('formats the time with the viewer locale', () => {
    expect(formatMessageTime('2026-10-23T10:05:00Z')).toMatch(/\d{1,2}[:.]05/)
  })

  it('labels today and yesterday, and uses a long date otherwise', () => {
    const now = new Date(2026, 9, 23, 12, 0)
    expect(formatDayLabel(new Date(2026, 9, 23, 8, 0).toISOString(), now)).toBe('Hoy')
    expect(formatDayLabel(new Date(2026, 9, 22, 23, 0).toISOString(), now)).toBe('Ayer')
    const older = formatDayLabel(new Date(2026, 9, 1, 12, 0).toISOString(), now)
    expect(older).not.toBe('Hoy')
    expect(older).toMatch(/1/)
    expect(formatDayLabel(new Date(2025, 9, 1, 12, 0).toISOString(), now)).toMatch(/2025/)
  })

  it('compares calendar days', () => {
    expect(isSameDay(new Date(2026, 0, 1, 1), new Date(2026, 0, 1, 23))).toBe(true)
    expect(isSameDay(new Date(2026, 0, 1, 23), new Date(2026, 0, 2, 0))).toBe(false)
  })
})
