# T18 — Notificaciones en tiempo real

- **Estado:** pendiente (T18a backend hecho en su PR; falta T18b frontend, que lo pasa a `hecho`)
- **Fase:** Transversal
- **Depende de:** T09, T15, T30a (hecho en la rama `feat/T30a-public-ids`; ids públicos y 404 uniforme)
- **Rama sugerida:** `feat/T18a-notifications-backend`, `feat/T18b-notifications-frontend`
- **Cierra bugs:** —
- **Tamaño:** L → 2 PRs (T18a backend, T18b frontend)
- **Spec:** refinada el 2026-10-24 contra el código de `feat/T30a-public-ids`.

## Objetivo
`Notifications.jsx` es un mock (lista fija de "me gusta" y comentarios que no existen). Hacen falta notificaciones reales dentro de la app, entregadas en tiempo real y con contador de no leídas. Las push (PWA) quedan para después de la demo (T26).

## Contexto (estado actual del código)
- `event/service/EventCancelled(Long eventId, String reason)` y `AttendeePromoted(Long eventId, Long userId)` **ya se publican** desde `EventService` (`cancel` y la promoción de la lista de espera) y nadie los escucha todavía. Son eventos internos (usan ids `bigint`); eso es correcto, el id público solo sale en el DTO.
- No existen aún eventos de dominio para: nueva seguidora (`user/service/FollowService#followUser`), apuntada a un evento (`EventService#join`), evento editado (`EventService#update`) ni "añadida a un grupo" (`MessageService#createGroupConversation`, y `addParticipants` de T12).
- Patrón a imitar para la entrega: `message/service/ChatMessageBroadcaster` (`@TransactionalEventListener(AFTER_COMMIT)` + `SimpMessagingTemplate#convertAndSendToUser(String.valueOf(userId), "/queue/...", dto)`; las sesiones WS se identifican por **id de usuaria**, no por username). `WebSocketConfig` ya habilita el broker en `/queue` y `/topic` con prefijo `/user`: no hace falta tocarlo.
- No hay `@EnableAsync` ni `@Async` en el proyecto. `SchedulingConfig` (`@Profile("!test")`) activa los jobs de mantenimiento; los existentes viven en `*/maintenance/` (p. ej. `CommunityMemberCounterMaintenanceService`).
- Entidades expuestas extienden `common/identifier/PublicIdEntity` (UUID v7 asignado en `@PrePersist`, columna `public_id uuid NOT NULL` + índice único, como en `V9__public_ids.sql`). `PublicIdArchitectureTest` falla en CI si un DTO de respuesta expone el `Long id`.
- Frontend: `pages/notifications/Notifications.{jsx,css}` (mock), `shared/ui/NavBar.jsx` (sin contador), `pages/settings/SettingsMenu.jsx` y `config/constants.js` (ya tienen la ruta de Ajustes → Notificaciones). El patrón de estado global por WS es `features/messages/components/conversations.provider.jsx` + `hooks/useWebSocket.js` (reconexión y recuperación de lo perdido, T11).

## Alcance

### T18a — Backend
**Migración `V<n>__notifications.sql`** (skill `/new-migration`; n = siguiente libre al empezar):
- `notifications`: `id bigserial PK`, `public_id uuid NOT NULL` (único), `recipient_id bigint NOT NULL` FK `user_accounts` `ON DELETE CASCADE`, `actor_id bigint NULL` FK `ON DELETE CASCADE` (la notificación de una usuaria borrada pierde sentido), `type varchar NOT NULL`, `target_type varchar NULL` (`EVENT` | `COMMUNITY` | `CONVERSATION` | `USER`), `target_ref varchar NULL` (la referencia **pública** del destino: UUID del evento o chat, `slug` de comunidad, `username`; nunca un `bigint`), `payload jsonb NOT NULL DEFAULT '{}'`, `read_at timestamptz NULL`, `created_at timestamptz NOT NULL DEFAULT now()`.
- Índices: `(recipient_id, created_at DESC, id DESC)`; parcial `(recipient_id) WHERE read_at IS NULL` para el contador; único `public_id`; parcial único de deduplicación de `NEW_FOLLOWER` no leído `(recipient_id, actor_id) WHERE type = 'NEW_FOLLOWER' AND read_at IS NULL`.
- `notification_preferences`: `(user_id, type)` PK, `enabled boolean NOT NULL`. Fila ausente = activada.

**Paquete nuevo `notification/`** (`domain`, `dto`, `repository`, `service`, `controller`, `maintenance`), mismo estilo que el resto:
- `domain/Notification extends PublicIdEntity`, `NotificationType` (enum), `NotificationTargetType`, `NotificationPreference`.
- Tipos de T18: `NEW_FOLLOWER`, `EVENT_JOINED` (a la organizadora, con aviso de lista de espera si procede), `EVENT_UPDATED`, `EVENT_CANCELLED`, `WAITLIST_PROMOTED`, `ADDED_TO_GROUP`. `EVENT_REMINDER` (T21), `COMMUNITY_JOIN_REQUEST` y `COMMUNITY_REQUEST_ACCEPTED` (T19) añaden su valor al enum en su propia tarea; el diseño no debe obligar a tocar nada más que el enum, el listener y el render del front.
- **Eventos de dominio nuevos** (records en el paquete del emisor, como `AttendeePromoted`): `UserFollowed`, `EventAttendeeJoined`, `EventUpdated` (con la lista de campos relevantes que han cambiado: fecha, hora, zona, lugar, no el resto), `AddedToGroup`. Se publican desde `FollowService`, `EventService` y `MessageService`/T12 dentro de la transacción del cambio.
- `service/NotificationListener`: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (añadir `@EnableAsync` con un executor acotado; hoy no existe) que crea la fila en una transacción **propia** (`REQUIRES_NEW`, porque tras el commit no queda transacción activa) y la empuja por WS. Un fallo al notificar nunca revierte ni rompe la acción que lo originó: se registra y se sigue.
- Reglas al crear: no notificar a quien hace la acción sobre sí misma; respetar `notification_preferences` (si el tipo está desactivado no se crea ni se envía); deduplicar `NEW_FOLLOWER` no leído de la misma seguidora (follow, unfollow, follow no genera una pila); cuando exista T13, no notificar a quien ha bloqueado al actor (punto de extensión, comentado en el listener).
- `EVENT_CANCELLED`, `EVENT_UPDATED` y `WAITLIST_PROMOTED` van a las asistentes `CONFIRMED`/`WAITLIST` según el caso, **en lotes** (un `INSERT ... SELECT` o `saveAll` por lotes, nunca una consulta por asistente) porque un evento puede tener cientos.
- **Sin texto en el servidor (D-019).** La fila guarda tipo + `payload` estructurado y el frontend compone el mensaje en español. Para eventos, el `payload` lleva el `title`, el instante de inicio en UTC **y el `timeZone` IANA del evento**; el cliente formatea la hora en la zona del evento y, si la suya es distinta, también en la suya. Nada de horas ya formateadas en `Europe/Madrid`.

**API** (`/api/notifications`, autenticadas, solo las suyas):
- `GET /api/notifications?limit=&before=` → `NotificationPageDto(items, hasMore, nextBefore)`. `limit` por defecto 20, **máximo 50** (se recorta, no falla). Orden `created_at DESC, id DESC`. Mismo patrón de cursor que el historial del chat (`before` = `public_id` de una notificación **suya**, si no 400 `Cursor no válido`); **T30c (cursores opacos, pendiente)** lo sustituirá por un token y este endpoint debe pasar por el mismo helper para que el cambio sea de un solo sitio.
- `NotificationDto(UUID id, NotificationType type, ActorDto actor{username, fullName, profilePictureUrl} | null, TargetDto target{type, ref} | null, Map<String,Object> payload, Instant readAt, Instant createdAt)`. Solo ids públicos; ningún `Long`.
- `GET /api/notifications/unread-count` → `{ count }`.
- `POST /api/notifications/read` con `{ ids: [UUID] }` (máx. 50) o `{ all: true }` → 204. Si algún id no existe **o es de otra usuaria** → 404 uniforme (`Notificación no encontrada`), sin distinguir los dos casos y sin marcar ninguna. Idempotente con las ya leídas.
- `GET /api/notifications/preferences` → `{ types: { NEW_FOLLOWER: true, ... } }` con todos los tipos. `PATCH /api/notifications/preferences` con `{ types: { NEW_FOLLOWER: false } }`: solo cambia los tipos presentes (D-020: ausente = sin cambios; aquí los valores son booleanos obligatorios, un `null` es 400, no "vaciar"). Un tipo desconocido es 400.
- Tiempo real: `convertAndSendToUser(String.valueOf(recipientId), "/queue/notifications", NotificationDto)` tras guardar. Nada de topics compartidos.
- Rate limiting: reutilizar el filtro de `infrastructure/ratelimit` para `POST /read`; el `GET` está acotado por el tamaño máximo.

**Mantenimiento** (`notification/maintenance/NotificationRetentionService`, `@Scheduled`, mismo patrón que los jobs existentes): borrar en lotes las leídas con más de 90 días y las no leídas con más de 180.

**Borrado de cuenta:** las FK `ON DELETE CASCADE` cubren `notifications` y `notification_preferences`; añadir la comprobación en el test de borrado de cuenta de `UserService#deleteUser` (BUG-064/065) para que una futura tabla sin cascada no lo rompa.

**Contrato:** actualizar `docs/api-contract-v1.json` con los endpoints y el DTO.

### T18b — Frontend
- `features/notifications/{services,hooks,components,utils}`: `notifications.service.js` (list, unreadCount, markRead, markAllRead, preferences) con `api-client`; `NotificationsProvider` (estilo `conversations.provider.jsx`) que carga el contador al montar, se suscribe a `/user/queue/notifications` con `useWebSocket`, incrementa el contador y antepone el item, y **al reconectar vuelve a pedir contador y primera página** (T11).
- `pages/notifications/Notifications.jsx` real: lista paginada con "Cargar más" (`before=nextBefore`), estado vacío, "Marcar todas como leídas", item no leído resaltado, clic = marcar como leída y navegar al destino (`/events/{id}`, `/communities/{slug}`, `/messages/{id}`, `/profile/{username}` según `target`). Formateador `notificationText(n)` por tipo en `utils/` (todo el texto en español, aquí y no en el servidor); fechas de evento con la zona del evento (D-019), reutilizando `features/events/utils`.
- `NavBar.jsx`: badge con el contador (se oculta a 0, "9+" a partir de 10), con `aria-label` accesible.
- Ajustes → Notificaciones: interruptores por tipo (`PATCH preferences`, optimista con rollback si falla).
- Retirar el mock y su CSS obsoleto.

## Fuera de alcance
- Push del navegador/PWA (T26) y correo.
- Notificaciones de mensajes de chat (el chat ya tiene su propio contador de no leídos).
- Agregación estilo "Ana y 3 más te siguen": no en v1. La lista muestra cada notificación; se valorará con datos reales.
- Notificaciones de T19/T21 (`COMMUNITY_*`, `EVENT_REMINDER`) y de T23: cada tarea añade su tipo.
- Cursores opacos (T30c).

## Criterios de aceptación
- [ ] Seguir a una usuaria, que alguien se apunte a un evento de la organizadora, editarlo (fecha, lugar), cancelarlo, ser promovida desde la lista de espera y ser añadida a un grupo generan la notificación correcta a la destinataria correcta y a nadie más.
- [ ] Llega por WS en menos de un segundo con la app abierta; con la app cerrada aparece al volver a entrar.
- [ ] Una acción de la usuaria sobre sí misma no se notifica; un tipo desactivado en preferencias no se crea.
- [ ] Follow, unfollow, follow repetido no apila `NEW_FOLLOWER` no leídos.
- [ ] El contador del `NavBar` coincide con `unread-count` y se actualiza al leer y al llegar una nueva, también tras reconectar.
- [ ] `limit` mayor que 50 se recorta a 50; cursor ajeno o inexistente → 400; marcar como leída una notificación ajena o inexistente → 404 idéntico y sin efectos.
- [ ] Ningún DTO ni mensaje WS contiene un id `bigint`; `PublicIdArchitectureTest` pasa.
- [ ] El payload de eventos lleva instante UTC + zona IANA y el front muestra la hora del lugar (D-019).
- [ ] Un fallo del listener no afecta a la acción original (p. ej. seguir sigue devolviendo 204/200).
- [ ] Borrar la cuenta borra sus notificaciones (recibidas y generadas por ella).

## Tests requeridos
- **IT** (Testcontainers, `AbstractIntegrationTest`): un caso por tipo (destinataria, actor, payload, preferencias); cancelar un evento con N asistentes crea N filas en lote; dedupe de `NEW_FOLLOWER`; paginación con cursor y tope de 50; 404 uniforme al marcar ajena/inexistente; `all: true`; preferencias (PATCH parcial, `null` y tipo desconocido → 400); autoacción no notifica; retención borra lo antiguo y respeta lo reciente; borrado de cuenta.
- **WS** (`StompTestClient`): la destinataria recibe en `/user/queue/notifications` y otra usuaria conectada no.
- **Unit**: el listener no propaga excepciones; construcción del payload con zona IANA.
- **Frontend (Vitest)**: servicio, provider (sube contador por WS, refresca al reconectar), `notificationText` por tipo, `NavBar` badge, página (vacío, cargar más, marcar leída y navegar), ajustes.

## Notas y riesgos
- `@Async` + `AFTER_COMMIT`: si el executor se satura se pierden notificaciones; usar cola acotada y política de descarte con log, nunca bloquear el hilo de la petición.
- Mantener `payload` pequeño y sin datos personales extra: solo lo necesario para pintar (título del evento, nombre del grupo); el nombre/foto de la actora salen del `actor` en el momento de leer.
- Ya hay `Long eventId` en `EventCancelled`/`AttendeePromoted`: el listener resuelve el `public_id` al crear el `target_ref`; no cambiar esos records.
- Si T12 aterriza antes, `AddedToGroup` se publica desde `addParticipants`; si no, desde `createGroupConversation` y T12 lo reutiliza.

## Notas de implementación de T18a
- Migración `V12__notifications.sql` (V11 es T13). La retención usa `created_at` para leídas (90 d) y no leídas (180 d), confirmado por Yago.
- Cursor de `before`: `OpaqueCursor` (tipo `n`), ya existente por T30c; el token solo nombra una notificación suya y la posición sale de su fila.
- `EventCancelled` ganó `actorId` (quién canceló): sin él no se puede evitar notificar a la organizadora de su propia acción ni avisarla si cancela la moderación.
- Estados de asistente en el código: `GOING` (en la spec, "CONFIRMED") y `WAITLIST`.
- Además del dedupe de `NEW_FOLLOWER`, `EVENT_JOINED` no leído de la misma persona al mismo evento también se deduplica (apuntarse/salir/apuntarse).
- Altas en grupo: `AddedToGroup` se publica también al crear el grupo (`MessageService#createGroupConversation`), no solo desde `addParticipants`.
- Las escrituras van por JDBC en lotes (el UUID v7 se genera en Java); `Notification` es la entidad de lectura y marcado.
- Cola de notificaciones acotada (2-4 hilos, cola de 500, descarte con log si se llena).

## Decisiones de Yago (2026-10-28) — implementadas en `feat/yago-decisions-t12-t13-t18-t19`
- **Límites por usuaria** (no por IP; detrás del proxy todas comparten dirección), generosos y configurables en `asaltamontes.rate-limit.*` (se desactivan con `enabled=false`, como en el perfil `test`). Al superarlos, `429` ProblemDetail con `Retry-After` (segundos). Cuentan todos los intentos, también los que acaban en error. Ventana fija en memoria (`RateLimiter`, D-007):
  | Acción | Propiedad | Por defecto |
  |---|---|---|
  | Editar un evento (`PATCH /api/events/{id}`) | `event-edits-per-hour` | 30 / hora |
  | Crear un grupo (`POST /api/conversations/group`) | `groups-created-per-day` | 10 / día |
  | Seguir (`POST /api/users/follow/{username}`) | `follows-per-hour` | 100 / hora |
  Componente: `UserActionLimiter` (en `infrastructure/ratelimit`), llamado desde los servicios. Tests: `RateLimitIT`.
- **Notificaciones de una actora bloqueada:** se **ocultan al leer** (listado y contador de no leídas) en cuanto la destinataria bloquea a la actora; **no se borran** y reaparecen si desbloquea. Es en un solo sentido (destinataria → actora). Marcar todo como leído sigue marcando también las ocultas. Test: `NotificationsIT.whenTheRecipientBlocksTheActor_...`.
- **Cooldown de 24 h de `NEW_FOLLOWER`:** aceptado tal como está.
