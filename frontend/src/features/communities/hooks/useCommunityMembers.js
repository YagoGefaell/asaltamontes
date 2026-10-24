import { useState, useEffect } from 'react'
import { getCommunityMembersRequest } from '../services/communities.service'

/**
 * Members of a community. Only members may see the list (BUG-066), so nothing is requested
 * unless `isMember`; the others see just the member count of the community.
 */
export function useCommunityMembers(communityId, isMember) {
  const [loaded, setLoaded] = useState({ communityId: null, list: [] })

  useEffect(() => {
    if (!communityId || !isMember) return undefined
    let cancelled = false
    getCommunityMembersRequest(communityId)
      .then((list) => { if (!cancelled) setLoaded({ communityId, list }) })
      .catch(() => { if (!cancelled) setLoaded({ communityId, list: [] }) })
    return () => { cancelled = true }
  }, [communityId, isMember])

  const visible = isMember && loaded.communityId === communityId
  return { members: visible ? loaded.list : [] }
}
