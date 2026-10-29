import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const patch = vi.fn()
vi.mock('../../../lib/api-client.js', () => ({
  apiClient: { get: (...args) => get(...args), patch: (...args) => patch(...args) },
}))

const { getCommunityChatRequest, setCommunityChatEnabledRequest } = await import('./communities.service.js')

describe('community chat requests (T20)', () => {
  beforeEach(() => {
    get.mockReset()
    patch.mockReset()
  })

  it('asks for the chat of the community by its public id', async () => {
    get.mockResolvedValue({ conversationId: 'chat-1', enabled: true, canWrite: false })

    await expect(getCommunityChatRequest('c-1')).resolves.toEqual({ conversationId: 'chat-1', enabled: true, canWrite: false })
    expect(get).toHaveBeenCalledWith('/api/communities/c-1/chat')
  })

  it('switches the chat on and off with a PATCH that only carries chatEnabled', async () => {
    patch.mockResolvedValue({})

    await setCommunityChatEnabledRequest('c-1', false)

    expect(patch).toHaveBeenCalledWith('/api/communities/c-1', { chatEnabled: false })
  })

  it('keeps the server message when the request fails', async () => {
    get.mockRejectedValue(new Error('Comunidad no encontrada'))

    await expect(getCommunityChatRequest('c-1')).rejects.toThrow('Comunidad no encontrada')
  })
})


describe("pending communities (T31c)", () => {
  it("normalizes the answer", async () => {
    const { getPendingCommunitiesRequest } = await import("./communities.service.js")
    get.mockResolvedValue({ toApprove: [{ pendingCount: 1 }] })
    await expect(getPendingCommunitiesRequest()).resolves.toEqual({ toApprove: [{ pendingCount: 1 }], mine: [] })
    expect(get).toHaveBeenCalledWith("/api/communities/me/pending")
  })
})
