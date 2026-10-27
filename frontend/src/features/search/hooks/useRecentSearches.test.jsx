import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { MAX_RECENTS, RECENT_KEY, useRecentSearches } from './useRecentSearches.js'

describe('useRecentSearches', () => {
  beforeEach(() => window.localStorage.clear())

  it('keeps the most recent first without duplicates and ignores tiny terms', () => {
    const { result } = renderHook(() => useRecentSearches())
    act(() => { result.current.add('laura'); result.current.add('x'); result.current.add('marta'); result.current.add('LAURA') })
    expect(result.current.recents).toEqual(['LAURA', 'marta'])
    expect(JSON.parse(window.localStorage.getItem(RECENT_KEY))).toEqual(['LAURA', 'marta'])
  })

  it('caps the list', () => {
    const { result } = renderHook(() => useRecentSearches())
    act(() => { for (let i = 0; i < MAX_RECENTS + 3; i++) result.current.add(`term ${i}`) })
    expect(result.current.recents).toHaveLength(MAX_RECENTS)
  })

  it('survives corrupt storage', () => {
    window.localStorage.setItem(RECENT_KEY, '{nope')
    const { result } = renderHook(() => useRecentSearches())
    expect(result.current.recents).toEqual([])
  })

  it('clears', () => {
    window.localStorage.setItem(RECENT_KEY, JSON.stringify(['ana']))
    const { result } = renderHook(() => useRecentSearches())
    act(() => result.current.clear())
    expect(result.current.recents).toEqual([])
  })
})
