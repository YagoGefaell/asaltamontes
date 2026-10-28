import { describe, expect, it } from 'vitest'
import { canDeleteMessage } from './messageModeration.js'

const by = (senderUsername, authorRole) => ({ id: 'm', senderUsername, authorRole, type: 'USER' })

describe('canDeleteMessage', () => {
  it('offers nothing to a plain member', () => {
    expect(canDeleteMessage(by('bea'), { moderationRole: null, currentUsername: 'ana' })).toBe(false)
  })

  it('lets the owner delete anyone, moderators included', () => {
    const ctx = { moderationRole: 'OWNER', currentUsername: 'ana' }
    expect(canDeleteMessage(by('bea'), ctx)).toBe(true)
    expect(canDeleteMessage(by('cris', 'MODERATOR'), ctx)).toBe(true)
    expect(canDeleteMessage(by('ana', 'OWNER'), ctx)).toBe(true)
  })

  it('keeps a moderator off the owner and the other moderators, but not off members or her own', () => {
    const ctx = { moderationRole: 'MODERATOR', currentUsername: 'mod' }
    expect(canDeleteMessage(by('bea'), ctx)).toBe(true)
    expect(canDeleteMessage(by('mod', 'MODERATOR'), ctx)).toBe(true)
    expect(canDeleteMessage(by('ana', 'OWNER'), ctx)).toBe(false)
    expect(canDeleteMessage(by('cris', 'MODERATOR'), ctx)).toBe(false)
  })

  it('never on system or already deleted messages', () => {
    const ctx = { moderationRole: 'OWNER', currentUsername: 'ana' }
    expect(canDeleteMessage({ type: 'SYSTEM' }, ctx)).toBe(false)
    expect(canDeleteMessage({ ...by('bea'), deleted: true }, ctx)).toBe(false)
  })
})
