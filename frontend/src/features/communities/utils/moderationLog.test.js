import { describe, expect, it } from 'vitest'
import { describeEntry } from './moderationLog'

describe('describeEntry', () => {
  it('describes a ban with its reason', () => {
    expect(describeEntry({ action: 'BAN', actorUsername: 'ana', targetUsername: 'eva', reason: 'spam' }))
      .toEqual({ text: '@ana expulsó a @eva', detail: 'Motivo: spam' })
  })
  it('names deleted accounts and lists the edited fields', () => {
    expect(describeEntry({ action: 'UNBAN', actorUsername: 'ana', targetUsername: null }).text).toBe('@ana readmitió a una cuenta eliminada')
    expect(describeEntry({ action: 'COMMUNITY_UPDATED', actorUsername: 'ana', reason: 'name,city' }))
      .toEqual({ text: '@ana editó los datos de la comunidad', detail: 'Cambios: nombre, ciudad' })
  })
  it('does not break on an unknown action', () => {
    expect(describeEntry({ action: 'NEW_THING', actorUsername: 'ana' }).text).toContain('@ana')
  })
})
