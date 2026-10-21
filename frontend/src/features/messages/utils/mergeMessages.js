/**
 * Combines chat messages from different sources (history pages, real-time WebSocket) into one list:
 * one entry per id, in chronological order (ids grow with time). If a message arrives twice, the
 * newest copy wins.
 */
export function mergeMessages(existing = [], incoming = []) {
  const byId = new Map()
  const withoutId = []

  for (const message of [...existing, ...incoming]) {
    if (message?.id == null) {
      withoutId.push(message)
    } else {
      byId.set(String(message.id), message)
    }
  }

  const ordered = [...byId.values()].sort((a, b) => Number(a.id) - Number(b.id))
  return [...ordered, ...withoutId]
}
