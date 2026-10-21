import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('../../../lib/api-client.js', () => ({ apiClient: { get: (...args) => get(...args) } }))

const { getMessagesAfterRequest } = await import('./messages.service.js')

const page = (ids, hasMore) => ({ items: ids.map((id) => ({ id })), hasMore, nextBefore: null })

describe('getMessagesAfterRequest (T11)', () => {
  beforeEach(() => get.mockReset())

  it('follows the pages from the last known id until there are no more', async () => {
    get.mockResolvedValueOnce(page([11, 12], true)).mockResolvedValueOnce(page([13], false))

    const result = await getMessagesAfterRequest(7, 10)

    expect(result).toEqual({ items: [{ id: 11 }, { id: 12 }, { id: 13 }], complete: true })
    expect(get).toHaveBeenNthCalledWith(1, '/api/conversations/7/messages?after=10&limit=100')
    expect(get).toHaveBeenNthCalledWith(2, '/api/conversations/7/messages?after=12&limit=100')
  })

  it('gives up after maxPages and says the result is incomplete', async () => {
    get.mockResolvedValue(page([1], true))

    const result = await getMessagesAfterRequest(7, 0, 3)

    expect(result.complete).toBe(false)
    expect(get).toHaveBeenCalledTimes(3)
  })
})
