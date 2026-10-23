# BUG-061 — Si falla la carga del historial, el chat parece vacío y se marca como leído

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/features/messages/components/conversations.provider.jsx:352-355` (`catch` que llama a `markConversationAsRead(conversationId)`).
- `frontend/src/pages/messages/components/ChatPanel.jsx:518-520` (texto "Empieza la conversacion con @…" cuando `currentMessages.length === 0`).

## Qué pasa
Si `GET /messages` falla (red, 5xx, 403), el provider ignora el error y además marca la conversación como leída: pone `unreadCount = 0` y manda `chat.read` (o `POST /read`) al servidor aunque la usuaria no haya visto ningún mensaje. El panel muestra "Empieza la conversacion con @nombre", como si el chat estuviera vacío, sin error ni reintento (y en un grupo escribe "@Nombre del grupo"). Se pierde el indicador de no leído de mensajes que nunca vio. El mismo texto aparece un instante mientras carga el historial de un chat existente.

## Cómo reproducir
Abrir un chat con mensajes sin leer con el backend parado (o devolviendo 500 al `GET /messages`).

## Arreglo propuesto
Guardar un `historyError` por conversación; no marcar como leído en el `catch`; mostrar "No se pudo cargar la conversación. Reintentar" en lugar del texto de chat vacío; mostrar el texto vacío solo cuando la carga terminó bien con 0 mensajes. Tests.
