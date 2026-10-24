import { describe, expect, it } from 'vitest'
import { mergeMessages } from './mergeMessages.js'

const msg = (id, content = `m${id}`) => ({ id: `0192f3a4-${String(id).padStart(4, '0')}`, content })
const ids = (list) => list.map((m) => Number(m.id.slice(9)))

describe('mergeMessages', () => {
  it('prepends an older history page in order', () => {
    expect(ids(mergeMessages([msg(4), msg(5)], [msg(1), msg(2), msg(3)]))).toEqual([1, 2, 3, 4, 5])
  })

  it('drops duplicates when a WebSocket message is also in the page being loaded', () => {
    const live = [msg(10, 'por ws')]
    const page = [msg(8), msg(9), msg(10, 'por historial')]

    const merged = mergeMessages(live, page)

    expect(ids(merged)).toEqual([8, 9, 10])
    expect(merged.filter((m) => m.id === msg(10).id)).toHaveLength(1)
  })

  it('appends a real-time message after the history', () => {
    expect(ids(mergeMessages([msg(1), msg(2)], [msg(3)]))).toEqual([1, 2, 3])
  })

  it('keeps one entry per id', () => {
    expect(mergeMessages([msg(7)], [{ id: msg(7).id, content: 'x' }])).toHaveLength(1)
  })

  it('handles empty inputs', () => {
    expect(mergeMessages()).toEqual([])
    expect(mergeMessages(undefined, [msg(1)])).toEqual([msg(1)])
  })
})
