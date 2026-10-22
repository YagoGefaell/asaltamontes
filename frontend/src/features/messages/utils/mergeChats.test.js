import { describe, expect, it } from 'vitest'
import { countUnreadChats, mergeChatPages, unreadChatsElsewhere } from './mergeChats.js'

const chat = (id, extra = {}) => ({ id, name: `chat ${id}`, unreadCount: 0, ...extra })

describe('mergeChatPages', () => {
  it('appends an older page and keeps the local copy of chats already loaded', () => {
    const existing = [chat(9, { lastMsg: 'nuevo por WebSocket' }), chat(8), chat(7)]
    const older = [chat(9, { lastMsg: 'antiguo' }), chat(6), chat(5)]

    const merged = mergeChatPages(existing, older)

    expect(merged.map((c) => c.id)).toEqual([9, 8, 7, 6, 5])
    expect(merged[0].lastMsg).toBe('nuevo por WebSocket')
  })

  it('puts a fresh first page on top and drops the old copies of its chats', () => {
    const existing = [chat(8), chat(7), chat(6), chat(5)]
    const fresh = [chat(5, { lastMsg: 'acaba de escribir' }), chat(8)]

    const merged = mergeChatPages(existing, fresh, { fresh: true })

    expect(merged.map((c) => c.id)).toEqual([5, 8, 7, 6])
    expect(merged[0].lastMsg).toBe('acaba de escribir')
  })

  it('compares ids as strings (WebSocket payloads may carry them as text)', () => {
    expect(mergeChatPages([chat('3')], [chat(3), chat(2)]).map((c) => String(c.id))).toEqual(['3', '2'])
  })
})

describe('unread chats', () => {
  it('counts chats, not messages, and what is left outside the loaded pages', () => {
    const loaded = [chat(1, { unreadCount: 4 }), chat(2), chat(3, { unreadCount: 1 })]

    expect(countUnreadChats(loaded)).toBe(2)
    expect(unreadChatsElsewhere(5, loaded)).toBe(3)
    expect(unreadChatsElsewhere(1, loaded)).toBe(0)
  })
})
