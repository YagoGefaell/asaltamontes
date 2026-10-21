import { describe, expect, it } from 'vitest'
import { reconnectDelay } from './reconnectDelay.js'

describe('reconnectDelay', () => {
  it('doubles from 1 s and stops at 30 s (no jitter at the top of the range)', () => {
    const top = () => 1
    expect([0, 1, 2, 3, 4, 5, 6, 10].map((attempt) => reconnectDelay(attempt, top)))
      .toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000])
  })

  it('adds jitter between half and the whole delay', () => {
    expect(reconnectDelay(3, () => 0)).toBe(4000)
    expect(reconnectDelay(3, () => 0.5)).toBe(6000)
    expect(reconnectDelay(10, () => 0)).toBe(15000)
  })
})
