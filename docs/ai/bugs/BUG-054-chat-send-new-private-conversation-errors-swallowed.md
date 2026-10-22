# BUG-054 — Al enviar el primer mensaje a una usuaria, si falla la creación de la conversación no pasa nada (sin error)

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/pages/messages/Messages.jsx:224-235` (`catch { return; }`).
- `frontend/src/features/messages/components/conversations.provider.jsx:162-169` (el error de la búsqueda previa también se silencia).

## Qué pasa
Al abrir un chat desde un perfil (`Profile.jsx`, sin `conversationId`), el primer envío llama a `ensurePrivateConversation`. Si el `POST /api/conversations/private` falla (usuaria inexistente, 403, 500, timeout, red), el `catch` hace `return` sin mostrar nada: el botón "Enviar" parece no hacer nada, el texto permanece y no hay explicación. Es lo contrario del tratamiento que ya reciben los errores de `/user/queue/errors` (`sendError`). Además, un fallo del `GET /private/{user}` distinto de 404 se trata como "no existe" y se intenta crear.

Relacionado: mientras dura el `await` el borrador no se vacía, así que dos "Enter" seguidos con `id: null` envían el mensaje dos veces (el segundo `ensurePrivateConversation` es idempotente, pero cada llamada publica su `chat.send`).

## Cómo reproducir
1. Entrar en el perfil de otra usuaria sin conversación previa y pulsar "Mensaje".
2. Parar el backend (o forzar un 4xx/5xx), escribir y enviar.
3. No aparece ningún mensaje de error.

## Arreglo propuesto
En el `catch`, `setSendError(error.message || "No se ha podido iniciar la conversación")`. En `ensurePrivateConversation`, caer a "crear" solo si el GET devuelve `null` (404) y propagar el resto. Bloquear `handleSendMessage` con un `sending` mientras dura el `await`. Tests.
