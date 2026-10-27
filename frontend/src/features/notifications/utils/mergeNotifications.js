/** Same ids appear once; the list keeps its order (newest first). */
export function mergeNotifications(current, incoming) {
  const seen = new Set(current.map((item) => item.id));
  return [...current, ...incoming.filter((item) => !seen.has(item.id))];
}
