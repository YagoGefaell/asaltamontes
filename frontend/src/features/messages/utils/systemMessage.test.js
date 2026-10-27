import { describe, expect, it } from 'vitest'
import { systemMessageText } from './systemMessage.js'

const message = (systemPayload, content = 'respaldo') => ({ type: 'SYSTEM', content, systemPayload })

describe('systemMessageText (T12)', () => {
  it('composes the text in Spanish from the payload, talking to the current user in second person', () => {
    expect(systemMessageText(message({ action: 'MEMBER_ADDED', actor: 'ana', targets: ['marta', 'lola'] }), 'bea'))
      .toBe('@ana añadió a @marta y @lola')
    expect(systemMessageText(message({ action: 'MEMBER_ADDED', actor: 'ana', targets: ['bea'] }), 'bea'))
      .toBe('@ana te añadió al grupo')
    expect(systemMessageText(message({ action: 'MEMBER_ADDED', actor: 'ana', targets: ['marta'] }), 'ana'))
      .toBe('Añadiste a @marta')
    expect(systemMessageText(message({ action: 'MEMBER_LEFT', actor: 'ana' }), 'ana')).toBe('Saliste del grupo')
    expect(systemMessageText(message({ action: 'MEMBER_REMOVED', actor: 'ana', targets: ['marta'] }), 'bea'))
      .toBe('@ana expulsó a @marta')
  })

  it('covers roles, name, image and history', () => {
    expect(systemMessageText(message({ action: 'ROLE_CHANGED', actor: 'ana', targets: ['bea'], role: 'ADMIN' }), 'bea'))
      .toBe('@ana te hizo administradora')
    expect(systemMessageText(message({ action: 'ROLE_CHANGED', actor: 'ana', targets: ['bea'], role: 'MEMBER' }), 'cris'))
      .toBe('@ana quitó el rol de administradora a @bea')
    expect(systemMessageText(message({ action: 'ADMIN_ASSIGNED', targets: ['bea'] }), 'bea'))
      .toBe('Ahora eres administradora del grupo')
    expect(systemMessageText(message({ action: 'GROUP_RENAMED', actor: 'ana', name: 'Trail' }), 'bea'))
      .toBe('@ana cambió el nombre del grupo a «Trail»')
    expect(systemMessageText(message({ action: 'GROUP_IMAGE_CHANGED', actor: 'ana', removed: true }), 'bea'))
      .toBe('@ana quitó la imagen del grupo')
    expect(systemMessageText(message({ action: 'HISTORY_VISIBILITY_CHANGED', actor: 'ana', visible: true }), 'ana'))
      .toBe('Activaste que las nuevas participantes vean el historial')
  })

  it('falls back to the content for unknown actions or a missing payload', () => {
    expect(systemMessageText(message({ action: 'SOMETHING_NEW' }, 'texto del servidor'), 'ana')).toBe('texto del servidor')
    expect(systemMessageText(message(null, 'texto del servidor'), 'ana')).toBe('texto del servidor')
  })
})

describe('systemMessageText of an event chat (T21)', () => {
  it('says the event was cancelled, with the reason if there is one', () => {
    expect(systemMessageText(message({ action: 'EVENT_CANCELLED', reason: 'Viento' }), 'bea')).toBe('La quedada se ha cancelado: Viento')
    expect(systemMessageText(message({ action: 'EVENT_CANCELLED', reason: null }), 'bea')).toBe('La quedada se ha cancelado')
  })

  it('says what changed and the new time at the place of the event', () => {
    const text = systemMessageText(message({
      action: 'EVENT_UPDATED', changed: ['startsAt', 'meetingPoint'], startsAt: '2026-11-01T22:30:00Z', timeZone: 'Asia/Tokyo',
    }), 'bea')
    expect(text).toMatch(/^Ha cambiado la hora y el lugar de la quedada\. Ahora: /)
    expect(text).toContain('07:30')
    expect(systemMessageText(message({ action: 'EVENT_UPDATED', changed: ['city'] }), 'bea')).toBe('Ha cambiado el lugar de la quedada')
  })
})
