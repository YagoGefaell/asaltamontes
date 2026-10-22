/**
 * Combines pages of the chat list (T10b). The server sends them most recent first, so:
 * - an older page goes after what is already loaded, skipping chats already there (a WebSocket message
 *   moved them up, and the local copy is newer);
 * - a fresh first page goes on top and replaces the copies of its chats; everything else is older.
 */
export function mergeChatPages(existing = [], page = [], { fresh = false } = {}) {
  if (fresh) {
    const freshIds = new Set(page.map((chat) => String(chat.id)))
    return [...page, ...existing.filter((chat) => !freshIds.has(String(chat.id)))]
  }
  const loadedIds = new Set(existing.map((chat) => String(chat.id)))
  return [...existing, ...page.filter((chat) => !loadedIds.has(String(chat.id)))]
}

export function countUnreadChats(chats = []) {
  return chats.reduce((total, chat) => total + (chat.unreadCount > 0 ? 1 : 0), 0)
}

/** Chats with unread messages that are not loaded yet, from the server's total (for the navigation badge). */
export function unreadChatsElsewhere(serverUnreadChats, loadedChats) {
  return Math.max(0, (serverUnreadChats || 0) - countUnreadChats(loadedChats))
}
