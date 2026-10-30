import { formatEventDateTime, formatViewerDateTime } from '../../events/utils/eventFormat.js'

/** Types in the order of the settings page, with the label of each switch. */
export const NOTIFICATION_TYPE_LABELS = {
  NEW_FOLLOWER: 'Alguien empieza a seguirme',
  FOLLOW_REQUEST: 'Alguien pide seguirme (perfil privado)',
  FOLLOW_REQUEST_APPROVED: 'Aceptan mi solicitud de seguimiento',
  EVENT_JOINED: 'Alguien se apunta a una quedada mía',
  EVENT_UPDATED: 'Cambian la hora o el lugar de una quedada en la que estoy',
  EVENT_CANCELLED: 'Se cancela una quedada en la que estoy',
  WAITLIST_PROMOTED: 'Consigo plaza desde la lista de espera',
  EVENT_REMINDER: 'Recordatorio de una quedada en la que estoy (24 h y 2 h antes)',
  ADDED_TO_GROUP: 'Me añaden a un grupo',
  POST_COMMENT: 'Comentan una publicación mía',
}

const FALLBACK = 'Tienes una notificación nueva'

function actorName(notification) {
  const actor = notification.actor
  if (!actor) return 'Alguien'
  return actor.fullName || `@${actor.username}`
}

function quoted(title) {
  return title ? `«${title}»` : 'una quedada'
}

// Time of the event at its own place (D-019) and, when the viewer's zone differs, in hers too
function eventWhen(payload, options) {
  if (!payload?.startsAt) return ''
  const { locale, viewerZone } = options
  const at = formatEventDateTime(payload.startsAt, payload.timeZone, locale, viewerZone)
  const mine = formatViewerDateTime(payload.startsAt, payload.timeZone, locale, viewerZone)
  return mine ? `${at} · en tu zona: ${mine}` : at
}

const CHANGED_LABELS = { startsAt: 'la hora', timezone: 'la hora', city: 'el lugar', meetingPoint: 'el lugar' }

function changedText(changed) {
  const parts = [...new Set((changed || []).map((field) => CHANGED_LABELS[field]).filter(Boolean))]
  if (parts.length === 0) return 'ha cambiado'
  return `ha cambiado ${parts.join(' y ')}`
}

/**
 * The message of a notification, in Spanish: the server only sends type + payload (D-019).
 * Returns { text, detail } (detail may be ''). `options` ({ locale, viewerZone }) is for tests.
 */
export function notificationText(notification, options = {}) {
  const payload = notification?.payload || {}
  switch (notification?.type) {
    case 'NEW_FOLLOWER':
      return { text: `${actorName(notification)} ha empezado a seguirte`, detail: '' }
    case 'FOLLOW_REQUEST':
      return { text: `${actorName(notification)} quiere seguirte`, detail: '' }
    case 'FOLLOW_REQUEST_APPROVED':
      return { text: `${actorName(notification)} ha aceptado tu solicitud de seguimiento`, detail: '' }
    case 'EVENT_JOINED':
      return {
        text: payload.waitlisted
          ? `${actorName(notification)} se ha apuntado a la lista de espera de ${quoted(payload.title)}`
          : `${actorName(notification)} se ha apuntado a ${quoted(payload.title)}`,
        detail: '',
      }
    case 'EVENT_UPDATED':
      return { text: `${quoted(payload.title)} ${changedText(payload.changed)}`, detail: eventWhen(payload, options) }
    case 'EVENT_CANCELLED':
      return { text: `${quoted(payload.title)} se ha cancelado`, detail: payload.reason || '' }
    case 'WAITLIST_PROMOTED':
      return { text: `¡Ya tienes plaza en ${quoted(payload.title)}!`, detail: eventWhen(payload, options) }
    case 'EVENT_REMINDER':
      return {
        text: payload.hoursBefore === 2
          ? `${quoted(payload.title)} empieza en 2 horas`
          : `Mañana tienes ${quoted(payload.title)}`,
        detail: eventWhen(payload, options),
      }
    case 'ADDED_TO_GROUP':
      return { text: `${actorName(notification)} te ha añadido al grupo «${payload.groupName || 'sin nombre'}»`, detail: '' }
    case 'POST_COMMENT':
      return { text: `${actorName(notification)} ha comentado tu publicación`, detail: payload.excerpt || '' }
    case 'COMMUNITY_JOIN_REQUEST':
      return { text: `${actorName(notification)} quiere unirse a «${payload.communityName || 'tu comunidad'}»`, detail: '' }
    case 'COMMUNITY_JOIN_APPROVED':
      return { text: `Tu solicitud para unirte a «${payload.communityName || 'la comunidad'}» ha sido aceptada`, detail: '' }
    case 'COMMUNITY_JOIN_REJECTED':
      return { text: `Tu solicitud para unirte a «${payload.communityName || 'la comunidad'}» no ha sido aceptada`, detail: '' }
    default:
      return { text: FALLBACK, detail: '' }
  }
}

/** Where a click goes: { to, state } for react-router's navigate, or null when there is nowhere to go. */
export function notificationDestination(notification) {
  const target = notification?.target
  if (!target?.ref) return null
  const ref = encodeURIComponent(target.ref)
  switch (target.type) {
    case 'EVENT':
      return { to: `/events/${ref}` }
    case 'COMMUNITY':
      // Moderation staff land on the pending requests
      if (notification.type === 'COMMUNITY_JOIN_REQUEST') return { to: `/communities/${ref}/settings/requests` }
      return { to: `/communities/${ref}` }
    case 'POST':
      return { to: `/posts/${ref}?comments=1` }
    case 'USER':
      // The owner answers requests in one place
      if (notification.type === 'FOLLOW_REQUEST') return { to: '/notifications/requests' }
      return { to: `/users/${ref}` }
    case 'CONVERSATION':
      // Messages opens the chat from this state
      return {
        to: '/messages',
        state: { selectedConversation: { conversationId: target.ref, name: notification.payload?.groupName } },
      }
    default:
      return null
  }
}

/** Relative age for the list ("ahora", "hace 5 min", "hace 3 h", "hace 2 d", then the date). */
export function formatAge(iso, now = Date.now(), locale) {
  const seconds = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return 'ahora'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `hace ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `hace ${hours} h`
  const days = Math.floor(hours / 24)
  if (days < 7) return `hace ${days} d`
  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(new Date(iso))
}
