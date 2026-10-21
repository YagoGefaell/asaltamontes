import { describe, expect, it } from 'vitest'
import { mergeMessages } from './mergeMessages.js'

const msg = (id, content = `m${id}`) => ({ id, content })

describe('mergeMessages', () => {
  it('prepends an older history page in order', () => {
    expect(mergeMessages([msg(4), msg(5)], [msg(1), msg(2), msg(3)]).map((m) => m.id)).toEqual([1, 2, 3, 4, 5])
  })

  it('drops duplicates when a WebSocket message is also in the page being loaded', () => {
    const live = [msg(10, 'por ws')]
    const page = [msg(8), msg(9), msg(10, 'por historial')]

    const merged = mergeMessages(live, page)

    expect(merged.map((m) => m.id)).toEqual([8, 9, 10])
    expect(merged.filter((m) => m.id === 10)).toHaveLength(1)
  })

  it('appends a real-time message after the history', () => {
    expect(mergeMessages([msg(1), msg(2)], [msg(3)]).map((m) => m.id)).toEqual([1, 2, 3])
  })

  it('treats numeric and string ids as the same message', () => {
    expect(mergeMessages([msg(7)], [{ id: '7', content: 'x' }])).toHaveLength(1)
  })

  it('handles empty inputs', () => {
    expect(mergeMessages()).toEqual([])
    expect(mergeMessages(undefined, [msg(1)])).toEqual([msg(1)])
  })
})
