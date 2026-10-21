# T09 — Seguridad y corrección del chat

- **Estado:** hecho
- **Fase:** 1 — Chat
- **Depende de:** T02, T04
- **Rama sugerida:** `fix/T09-chat-security`
- **Cierra bugs:** BUG-001, BUG-016, BUG-017, BUG-018 (parte backend), BUG-019, BUG-021, BUG-022, BUG-030
- **Tamaño:** M

## División en sub-PRs (2026-10-21)
- **T09a — backend** (hecho): puntos 1-11 del alcance. Cierra BUG-016, BUG-017, BUG-019, BUG-021 y BUG-022, y la parte backend de BUG-018. Además, `StompErrorHandler`: los frames `ERROR` llevan el motivo real ("Token inválido", "Suscripción no permitida").
- **T09b — frontend** (hecho, #30): `userId` (BUG-030), errores de envío en el `ChatPanel` devolviendo el texto al input, y `maxLength=2000` con contador.

## Objetivo
El chat es la prioridad nº 1 y hoy cualquier usuaria autenticada puede escribir en cualquier conversación. Cerrar los agujeros de seguridad y corrección del flujo de mensajes antes de añadir funcionalidad.

## Contexto
- `message/service/MessageService.java`
  - `processIncomingMessage` → `saveMessage` **no comprueba** que la remitente sea participante.
  - Incremento de `unreadCount` leyendo y guardando cada participante (`for` + `save`).
  - `sendMessageToConversation` se ejecuta dentro de la transacción (antes del commit).
  - `createOrGetPrivateConversation` permite `recipient == current`.
  - `content` sin validar.
- `message/controller/ChatController.java`: `principal.getName()` → NPE si la conexión no está autenticada.
- `infrastructure/security/WebSocketJwtInterceptor.java`: `CONNECT` sin token o con token inválido/caducado se **acepta** como anónimo.
- Principal WS = username al conectar; `convertAndSendToUser(username, …)`. Si la usuaria cambia su username (`PATCH /api/users/me`) deja de recibir mensajes.
- Frontend `conversations.provider.jsx` compara `currentUser?.id`, pero el DTO `UserSelfDto` expone `userId`.

## Alcance
### Backend
1. **Autorización de envío**: `saveMessage` busca el participante `(conversationId, senderId)`; si no existe → `ForbiddenException`. Cargar la remitente por **id** (no por username).
2. **Validación**: `SendMessageCommandDto` con `@NotNull conversationId`, `@NotBlank @Size(max = 2000) content` (validar en `@MessageMapping` con `@Valid` o en el servicio). Recortar espacios. Rechazar contenido solo con espacios.
3. **Unread atómico**: `@Modifying UPDATE ConversationParticipant p SET p.unreadCount = p.unreadCount + 1 WHERE p.conversation.id = :cid AND p.user.id <> :senderId` (una sola sentencia).
4. **Actualizar la conversación** (`lastMessage`, `lastMessageAt`, `lastSender`) también con una sentencia `UPDATE` (evita lost updates entre mensajes simultáneos; o bloqueo optimista `@Version`).
5. **Envío tras commit**: publicar un `MessageSentEvent` y enviarlo por WS desde un `@TransactionalEventListener(phase = AFTER_COMMIT)`. Las ids de participantes se resuelven con una consulta de proyección (solo ids), no cargando entidades.
6. **Principal por id**: el `Principal` del WS tiene `getName()` = id de usuaria (String). `convertAndSendToUser(String.valueOf(userId), "/queue/messages", dto)`. Ajustar `ChatController` para obtener la id.
7. **CONNECT estricto**: sin token, token inválido, caducado o de tipo refresh → lanzar excepción en el interceptor (el cliente recibe frame `ERROR` y se cierra). Bloquear también `SEND`/`SUBSCRIBE` sin usuario autenticado.
8. **Restringir suscripciones**: solo `/user/queue/**` (y en el futuro `/topic/...` autorizados). Rechazar `SUBSCRIBE` a destinos arbitrarios (`/topic/**` hoy no se usa).
9. **Chat consigo misma**: `createOrGetPrivateConversation` con el mismo usuario → `BadRequestException`.
10. `markConversationAsRead` también con `UPDATE` directo.
11. Grupos: tamaño máximo configurable (p. ej. 100 participantes) en `createGroupConversation`.

### Frontend
- `conversations.provider.jsx`: usar `currentUser?.userId`.
- Mostrar los errores de envío que llegan por `/user/queue/errors` (T04) en el `ChatPanel`, devolviendo el texto al input si falla.
- Contador de caracteres / `maxLength=2000` en el input del chat.

## Notas
- D-012: el cambio en `WebSocketJwtInterceptor` (rechazar el CONNECT, principal por id) se hace dentro de la clase existente, sin reestructurarla.

## Fuera de alcance
- Paginación → T10. Reconexión con token renovado en el cliente → T11.

## Criterios de aceptación
- [ ] Una usuaria que no participa en la conversación X no puede enviar a X (test) ni leer su historial (ya existía, añadir test).
- [ ] 50 mensajes enviados concurrentemente a un grupo producen exactamente `unreadCount = 50` para cada receptora.
- [ ] Si la transacción de guardado falla, no se emite nada por WS.
- [ ] CONNECT sin token o con token caducado se rechaza.
- [ ] Tras cambiar el username, la usuaria sigue recibiendo mensajes sin reconectar.
- [ ] Mensaje vacío o > 2000 caracteres → error visible y no se guarda.

## Tests requeridos
- IT con cliente STOMP real (`WebSocketStompClient` + `StompSession`) contra `RANDOM_PORT`: envío autorizado, no autorizado, CONNECT sin token, recepción por la destinataria.
- IT de concurrencia del unread (ExecutorService, 50 hilos).
- Unit test del listener AFTER_COMMIT (no envía si hay rollback).
