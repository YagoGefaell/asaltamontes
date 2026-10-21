const BASE_DELAY_MS = 1000
const MAX_DELAY_MS = 30000

/**
 * Wait before reconnection attempt number `attempt` (0, 1, 2…): exponential from 1 s up to 30 s, with
 * jitter (between half and the whole of it) so that every client does not reconnect at the same time
 * after a deploy (T11).
 */
export function reconnectDelay(attempt, random = Math.random) {
  const ceiling = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** Math.max(0, attempt))
  return Math.round(ceiling / 2 + random() * (ceiling / 2))
}
