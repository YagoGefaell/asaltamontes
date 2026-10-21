# BUG-048 — Al caerse el WebSocket, el cleanup de las suscripciones lanza un TypeError

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado el `TypeError` (test); la pantalla de error, probable (así trata React 19 los errores en cleanups de efectos)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** T11
- **Detectado:** 2026-10-21, Claude durante T11

## Dónde
`frontend/src/features/messages/hooks/useGlobalMessageListener.js` → cleanup del `useEffect` (`subscription.unsubscribe()`)

## Qué pasa
Cuando el socket se cierra, `useWebSocket` pone `websocketClient = null` y `websocketConnected = false`, y React ejecuta el cleanup del listener, que llama a `unsubscribe()`. stompjs lanza `TypeError: There is no underlying STOMP connection` si ya no hay conexión. Un error en el cleanup de un efecto se propaga como un error de render: el `ErrorBoundary` más cercano sustituye la pantalla, o sin él se desmonta la app. Es decir, cada corte de red o cada deploy podía dejar la app en la pantalla de error en vez de reconectar.

## Cómo reproducir
Test `useGlobalMessageListener.test.js` → "does not throw when the connection is already gone on cleanup": con el código anterior falla con ese `TypeError`.

## Arreglo propuesto
Ignorar el error de `unsubscribe()` cuando la conexión ya no existe (el servidor ya descartó la suscripción). Hecho en T11.
