// Mirrors the hierarchy the server enforces (T20c, T20e) so the delete action is not offered where it would answer 403:
// the owner deletes anything, a moderator deletes her own and those of plain members, never the owner's or other
// moderators'. The 403 of the server stays as the real defense.
export const AUTHOR_ROLE_LABELS = {
  OWNER: 'Propietaria',
  MODERATOR: 'Moderadora',
}

export function canDeleteMessage(message, { moderationRole, currentUsername }) {
  if (!moderationRole || message?.type === 'SYSTEM' || message?.deleted) return false
  if (message.senderUsername && message.senderUsername === currentUsername) return true
  if (moderationRole === 'OWNER') return true
  return !message.authorRole
}
