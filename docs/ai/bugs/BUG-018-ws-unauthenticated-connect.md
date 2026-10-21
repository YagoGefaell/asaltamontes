# BUG-018 — El WebSocket acepta conexiones sin token o con token caducado y el chat deja de funcionar en silencio

- **Estado:** abierto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/chat + frontend
- **Tarea que lo resuelve:** T09 (backend), T11 (frontend)
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/security/WebSocketJwtInterceptor.java`; `backend/src/main/java/io/github/yagogefaell/asaltamontes/message/controller/ChatController.java`; `frontend/src/features/messages/hooks/useWebSocket.js`

## Qué pasa
Si el `CONNECT` llega sin `Authorization` o con un token caducado, el interceptor no hace nada y la sesión queda anónima. Las suscripciones a `/user/queue/messages` no reciben nada y `chat.send` falla con NPE en `principal.getName()`. Como el access token dura 15 minutos y el cliente reconecta (`reconnectDelay`) con el token en memoria, tras una caída de red o un deploy el chat deja de recibir mensajes sin avisar a la usuaria.

## Cómo reproducir
Abrir la app, esperar más de 15 minutos, reiniciar el backend → el cliente reconecta pero no recibe mensajes.

**Progreso (T09a):** parte backend resuelta: el `CONNECT` sin token, con token inválido, caducado o de refresh se rechaza con un frame `ERROR` cuyo `message` explica el motivo, y `SEND`/`SUBSCRIBE` sin usuaria se rechazan. Falta la parte del cliente (reconectar con el token renovado): T11.

## Arreglo propuesto
Rechazar el CONNECT no autenticado (T09) y renovar el token antes de reconectar (T11).
