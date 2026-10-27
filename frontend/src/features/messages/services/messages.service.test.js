import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const patch = vi.fn()
const del = vi.fn()
vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { get: (...args) => get(...args), post: (...args) => post(...args), patch: (...args) => patch(...args), delete: (...args) => del(...args) },
}))

const {
  getMessagesAfterRequest, getMyConversationsRequest, getConversationMessagesRequest, getGroupDetailsRequest, updateGroupRequest,
  addParticipantsRequest, removeParticipantRequest, changeParticipantRoleRequest, leaveGroupRequest,
} = await import('./messages.service.js')

const page = (ids, hasMore) => ({ items: ids.map((id) => ({ id, cursor: `c${id}` })), hasMore, nextBefore: null })

describe('getMessagesAfterRequest (T11)', () => {
  beforeEach(() => get.mockReset())

  it('follows the pages from the last known id until there are no more', async () => {
    get.mockResolvedValueOnce(page([11, 12], true)).mockResolvedValueOnce(page([13], false))

    const result = await getMessagesAfterRequest(7, 'c10')

    expect(result).toEqual({
      items: [{ id: 11, cursor: 'c11' }, { id: 12, cursor: 'c12' }, { id: 13, cursor: 'c13' }],
      complete: true,
    })
    expect(get).toHaveBeenNthCalledWith(1, '/api/conversations/7/messages?after=c10&limit=100')
    expect(get).toHaveBeenNthCalledWith(2, '/api/conversations/7/messages?after=c12&limit=100')
  })

  it('gives up after maxPages and says the result is incomplete', async () => {
    get.mockResolvedValue(page([1], true))

    const result = await getMessagesAfterRequest(7, 0, 3)

    expect(result.complete).toBe(false)
    expect(get).toHaveBeenCalledTimes(3)
  })
})

describe('opaque cursors (T30c)', () => {
  beforeEach(() => get.mockReset())

  it('asks for the next page of the chat list with the single before token', async () => {
    get.mockResolvedValue({ items: [], hasMore: true, nextBefore: 'tok_2', unreadChats: 3 })

    const result = await getMyConversationsRequest({ before: 'tok_1' })

    expect(get).toHaveBeenCalledWith('/api/conversations?before=tok_1')
    expect(result).toMatchObject({ hasMore: true, nextBefore: 'tok_2', unreadChats: 3 })
  })

  it('does not send any cursor for the first page of the chat list', async () => {
    get.mockResolvedValue({ items: [], hasMore: false })

    const result = await getMyConversationsRequest()

    expect(get).toHaveBeenCalledWith('/api/conversations')
    expect(result.nextBefore).toBeNull()
  })

  it('url-encodes the history cursor and returns nextBefore untouched', async () => {
    get.mockResolvedValue({ items: [], hasMore: true, nextBefore: 'abc_-' })

    const result = await getConversationMessagesRequest('chat-1', 'a+b/c=')

    expect(get).toHaveBeenCalledWith('/api/conversations/chat-1/messages?before=a%2Bb%2Fc%3D')
    expect(result.nextBefore).toBe('abc_-')
  })

  it('reports an incomplete catch-up if a page has more but no cursor to continue from', async () => {
    get.mockResolvedValue({ items: [{ id: 1 }], hasMore: true })

    const result = await getMessagesAfterRequest(7, 'c0')

    expect(result.complete).toBe(false)
    expect(get).toHaveBeenCalledTimes(1)
  })
})

describe('group management requests (T12)', () => {
  beforeEach(() => [get, post, patch, del].forEach((mock) => mock.mockReset()))

  it('calls each endpoint with its method and body', async () => {
    await getGroupDetailsRequest('g1', { page: 2, size: 10 })
    await updateGroupRequest('g1', { groupName: 'Nuevo' })
    await addParticipantsRequest('g1', ['bea'])
    await removeParticipantRequest('g1', 'bea')
    await changeParticipantRoleRequest('g1', 'bea', 'ADMIN')
    await leaveGroupRequest('g1')

    expect(get).toHaveBeenCalledWith('/api/conversations/g1?page=2&size=10')
    expect(patch).toHaveBeenCalledWith('/api/conversations/g1', { groupName: 'Nuevo' })
    expect(post).toHaveBeenCalledWith('/api/conversations/g1/participants', { usernames: ['bea'] })
    expect(del).toHaveBeenCalledWith('/api/conversations/g1/participants/bea')
    expect(patch).toHaveBeenCalledWith('/api/conversations/g1/participants/bea', { role: 'ADMIN' })
    expect(post).toHaveBeenCalledWith('/api/conversations/g1/leave')
  })

  it('keeps the message of the server when a request is refused', async () => {
    patch.mockRejectedValue(new Error('Solo las administradoras del grupo pueden hacerlo'))

    await expect(updateGroupRequest('g1', {})).rejects.toThrow('Solo las administradoras')
  })
})
