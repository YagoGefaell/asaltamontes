import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../services/communities.service', () => ({
  getCommunityMembersRequest: vi.fn(),
}))

import { getCommunityMembersRequest } from '../services/communities.service'
import { useCommunityMembers } from './useCommunityMembers.js'

describe('useCommunityMembers', () => {
  beforeEach(() => vi.clearAllMocks())

  it('does not request the list when she is not a member', () => {
    const { result } = renderHook(() => useCommunityMembers('c1', false))
    expect(getCommunityMembersRequest).not.toHaveBeenCalled()
    expect(result.current.members).toEqual([])
  })

  it('loads the list when she is a member', async () => {
    getCommunityMembersRequest.mockResolvedValue([{ username: 'ana' }])
    const { result } = renderHook(() => useCommunityMembers('c1', true))
    await waitFor(() => expect(result.current.members).toEqual([{ username: 'ana' }]))
    expect(getCommunityMembersRequest).toHaveBeenCalledWith('c1')
  })

  it('shows no list if the request fails (e.g. banned)', async () => {
    getCommunityMembersRequest.mockRejectedValue(new Error('404'))
    const { result } = renderHook(() => useCommunityMembers('c1', true))
    await waitFor(() => expect(getCommunityMembersRequest).toHaveBeenCalled())
    expect(result.current.members).toEqual([])
  })
})
