export const STATUS_LABELS = {
  OPEN: 'Abierta',
  DISMISSED: 'Descartada',
  ACTIONED: 'Resuelta',
}

export const TYPE_LABELS = {
  USER: 'Usuaria',
  MESSAGE: 'Mensaje',
  POST: 'Publicación',
  COMMENT: 'Comentario',
  EVENT: 'Quedada',
  COMMUNITY: 'Comunidad',
}

export const REASON_LABELS = {
  SPAM: 'Spam',
  HARASSMENT: 'Acoso',
  HATE: 'Odio',
  INAPPROPRIATE: 'Contenido inapropiado',
  DANGEROUS: 'Peligroso',
  FAKE_ACCOUNT: 'Cuenta falsa',
  OTHER: 'Otro motivo',
}

/** What "remove content" does for each type, for the confirmation sheet. USER has no such action (the API answers 400). */
export const REMOVE_EFFECT = {
  POST: 'Se borrará la publicación.',
  COMMENT: 'Se borrará el comentario.',
  MESSAGE: 'Se borrará el mensaje.',
  EVENT: 'Se cancelará la quedada.',
  COMMUNITY: 'Se borrará la comunidad (solo administradoras).',
}

export function formatDateTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}
