# T10 — Paginación por cursor del historial de chat

- **Estado:** pendiente
- **Fase:** 1 — Chat
- **Depende de:** T09 (y el índice `messages (conversation_id, id DESC)` de T05; si T05 no está mergeada, crear el índice aquí)
- **Rama sugerida:** `perf/T10-chat-pagination`
- **Cierra bugs:** BUG-020
- **Tamaño:** M

## Objetivo
Abrir un chat no debe descargar todos sus mensajes. Cargar los últimos N y pedir más al hacer scroll hacia arriba.

## Contexto
- `MessageRepository.findMessagesByConversationId` devuelve **todo** ordenado ASC.
- `GET /api/conversations/{id}/messages` → `List<MessageDto>`.
- `GET /api/conversations` (lista de chats) tampoco está paginada.
- Frontend: `conversations.provider.jsx#loadConversationMessages` guarda el array completo en `chatMessages[id]`; `ChatPanel.jsx` lo pinta.

## Alcance
### Backend
- `GET /api/conversations/{id}/messages?before=<messageId>&limit=<n>` (limit por defecto 30, máximo 100). Orden `id DESC` en la consulta, devuelto en orden cronológico ASC.
- Respuesta: `{ "items": [...], "hasMore": true, "nextBefore": 123 }` (record `MessagePageDto`).
- Cursor por `id` (monótono, IDENTITY) en vez de por `sentAt` (evita empates).
- Marcar como leído deja de hacerse en `GET` (efecto lateral en una lectura); se hace solo vía `chat.read` o un `POST /api/conversations/{id}/read`. **Coordinar con el frontend en la misma PR.**
- `GET /api/conversations?limit&before=<lastMessageAt,id>` paginado por cursor sobre `(last_message_at DESC NULLS LAST, id DESC)` + índice correspondiente (migración).
- Actualizar `docs/api-contract-v1.json`.

### Frontend
- Carga inicial: última página. `IntersectionObserver` en un centinela arriba del listado → carga la siguiente página conservando la posición de scroll.
- Deduplicar mensajes por `id` al combinar páginas y los que llegan por WS (puede llegar un mensaje por WS mientras se carga el historial).
- Lista de conversaciones con "cargar más" o scroll infinito.

## Criterios de aceptación
- [ ] Abrir un chat de 10 000 mensajes descarga como mucho 30.
- [ ] Hacer scroll hacia arriba carga los anteriores sin saltos de scroll.
- [ ] No hay mensajes duplicados ni perdidos cuando llegan mensajes en tiempo real durante la paginación.
- [ ] La consulta usa índice (EXPLAIN en la PR).

## Tests requeridos
- IT backend: páginas consecutivas cubren todo el historial sin solapes; `hasMore=false` al final; límite máximo respetado; no participante → 403.
- Test frontend (Vitest) de la función de merge/dedupe de mensajes.
