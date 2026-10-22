import { describe, expect, it } from 'vitest'
import { communityPath } from './communityPath.js'

describe('communityPath', () => {
  it('uses the slug', () => {
    expect(communityPath({ slug: 'trail-vigo-sur-1', name: 'Trail Vigo / Sur #1' })).toBe('/communities/trail-vigo-sur-1')
    expect(communityPath({ slug: 'trail-vigo' }, '/settings')).toBe('/communities/trail-vigo/settings')
  })

  it('encodes the name when there is no slug (a "/" or "#" must not break the URL)', () => {
    expect(communityPath({ name: 'Trail Vigo / Sur #1' })).toBe('/communities/Trail%20Vigo%20%2F%20Sur%20%231')
  })
})
