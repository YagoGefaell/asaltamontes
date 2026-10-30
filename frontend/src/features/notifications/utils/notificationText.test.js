import { describe, expect, it } from 'vitest'
import { formatAge, notificationDestination, notificationText } from './notificationText.js'

const ana = { username: 'ana', fullName: 'Ana Ruiz' }
const event = { title: 'Trail nocturno', startsAt: '2026-11-01T22:30:00Z', timeZone: 'Asia/Tokyo' }
const opts = { locale: 'es-ES', viewerZone: 'Europe/Madrid' }

describe('notificationText', () => {
  it('follower', () => {
    expect(notificationText({ type: 'NEW_FOLLOWER', actor: ana, payload: {} })).toEqual({
      text: 'Ana Ruiz ha empezado a seguirte', detail: '',
    })
  })

  it('follow requests of a private profile (T23a)', () => {
    expect(notificationText({ type: 'FOLLOW_REQUEST', actor: ana, payload: {} }).text).toBe('Ana Ruiz quiere seguirte')
    expect(notificationText({ type: 'FOLLOW_REQUEST_APPROVED', actor: ana, payload: {} }).text)
      .toBe('Ana Ruiz ha aceptado tu solicitud de seguimiento')
    const target = { type: 'USER', ref: 'ana' }
    expect(notificationDestination({ type: 'FOLLOW_REQUEST', target }).to).toBe('/notifications/requests')
    expect(notificationDestination({ type: 'FOLLOW_REQUEST_APPROVED', target }).to).toBe('/users/ana')
  })

  it('falls back to @username when there is no full name', () => {
    expect(notificationText({ type: 'NEW_FOLLOWER', actor: { username: 'ana' }, payload: {} }).text).toBe('@ana ha empezado a seguirte')
  })

  it('someone joins, or joins the waitlist', () => {
    expect(notificationText({ type: 'EVENT_JOINED', actor: ana, payload: { title: 'Trail' } }).text)
      .toBe('Ana Ruiz se ha apuntado a «Trail»')
    expect(notificationText({ type: 'EVENT_JOINED', actor: ana, payload: { title: 'Trail', waitlisted: true } }).text)
      .toBe('Ana Ruiz se ha apuntado a la lista de espera de «Trail»')
  })

  it('an updated event says what changed and shows the time at the event place and in the viewer zone', () => {
    const result = notificationText({ type: 'EVENT_UPDATED', payload: { ...event, changed: ['startsAt', 'meetingPoint'] } }, opts)
    expect(result.text).toBe('«Trail nocturno» ha cambiado la hora y el lugar')
    // 22:30 UTC is 07:30 (+1 day) in Tokyo and 23:30 in Madrid
    expect(result.detail).toContain('07:30')
    expect(result.detail).toContain('Asia/Tokyo')
    expect(result.detail).toContain('23:30')
  })

  it('reminders say how long is left and show the time at the event place and in the viewer zone', () => {
    const tomorrow = notificationText({ type: 'EVENT_REMINDER', payload: { ...event, hoursBefore: 24 } }, opts)
    expect(tomorrow.text).toBe('Mañana tienes «Trail nocturno»')
    expect(tomorrow.detail).toContain('07:30')
    expect(tomorrow.detail).toContain('23:30')
    expect(notificationText({ type: 'EVENT_REMINDER', payload: { ...event, hoursBefore: 2 } }, opts).text)
      .toBe('«Trail nocturno» empieza en 2 horas')
  })

  it('does not repeat the time when the viewer is in the event zone', () => {
    const result = notificationText({ type: 'WAITLIST_PROMOTED', payload: event }, { locale: 'es-ES', viewerZone: 'Asia/Tokyo' })
    expect(result.text).toBe('¡Ya tienes plaza en «Trail nocturno»!')
    expect(result.detail).not.toContain('en tu zona')
  })

  it('cancelled event shows the reason', () => {
    expect(notificationText({ type: 'EVENT_CANCELLED', payload: { title: 'Trail', reason: 'Viento' } }))
      .toEqual({ text: '«Trail» se ha cancelado', detail: 'Viento' })
  })

  it('added to a group', () => {
    expect(notificationText({ type: 'ADDED_TO_GROUP', actor: ana, payload: { groupName: 'Domingueras' } }).text)
      .toBe('Ana Ruiz te ha añadido al grupo «Domingueras»')
  })

  it('comment on a post', () => {
    expect(notificationText({ type: 'POST_COMMENT', actor: ana, payload: { excerpt: '¡Qué bonita ruta!' } }))
      .toEqual({ text: 'Ana Ruiz ha comentado tu publicación', detail: '¡Qué bonita ruta!' })
  })

  it('unknown types still render something', () => {
    expect(notificationText({ type: 'FUTURE_TYPE' }).text).toBe('Tienes una notificación nueva')
  })
})

describe('notificationDestination', () => {
  it('maps each target type to a route', () => {
    expect(notificationDestination({ target: { type: 'EVENT', ref: 'u-1' } })).toEqual({ to: '/events/u-1' })
    expect(notificationDestination({ type: 'POST_COMMENT', target: { type: 'POST', ref: 'p-1' } })).toEqual({ to: '/posts/p-1?comments=1' })
    expect(notificationDestination({ target: { type: 'COMMUNITY', ref: 'trail-vigo' } })).toEqual({ to: '/communities/trail-vigo' })
    expect(notificationDestination({ target: { type: 'USER', ref: 'ana' } })).toEqual({ to: '/users/ana' })
  })

  it('opens a chat through the Messages state', () => {
    const destination = notificationDestination({ target: { type: 'CONVERSATION', ref: 'c-1' }, payload: { groupName: 'G' } })
    expect(destination.to).toBe('/messages')
    expect(destination.state.selectedConversation).toEqual({ conversationId: 'c-1', name: 'G' })
  })

  it('has nowhere to go without a target', () => {
    expect(notificationDestination({ target: null })).toBeNull()
  })
})

describe('community join notifications', () => {
  const payload = { communityName: 'Trail Madrid' }

  it('words the three types', () => {
    expect(notificationText({ type: 'COMMUNITY_JOIN_REQUEST', actor: { username: 'ana', fullName: 'Ana' }, payload }).text)
      .toBe('Ana quiere unirse a «Trail Madrid»')
    expect(notificationText({ type: 'COMMUNITY_JOIN_APPROVED', payload }).text).toContain('aceptada')
    expect(notificationText({ type: 'COMMUNITY_JOIN_REJECTED', payload }).text).toContain('no ha sido aceptada')
  })

  it('sends staff to the requests screen and the requester to the community', () => {
    const target = { type: 'COMMUNITY', ref: 'trail-madrid' }
    expect(notificationDestination({ type: 'COMMUNITY_JOIN_REQUEST', target }).to).toBe('/communities/trail-madrid/settings/requests')
    expect(notificationDestination({ type: 'COMMUNITY_JOIN_APPROVED', target }).to).toBe('/communities/trail-madrid')
  })
})

describe('formatAge', () => {
  const now = Date.parse('2026-10-27T12:00:00Z')
  it('is relative for recent ones', () => {
    expect(formatAge('2026-10-27T11:59:50Z', now)).toBe('ahora')
    expect(formatAge('2026-10-27T11:55:00Z', now)).toBe('hace 5 min')
    expect(formatAge('2026-10-27T09:00:00Z', now)).toBe('hace 3 h')
    expect(formatAge('2026-10-25T12:00:00Z', now)).toBe('hace 2 d')
  })
})
