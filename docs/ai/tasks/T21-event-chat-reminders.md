# T21 — Chat de evento, recordatorios y exportación a calendario

- **Estado:** hecho
- **Fase:** 3b — Eventos
- **Depende de:** T12, T16, T18
- **Rama sugerida:** `feat/T21-event-chat-reminders`
- **Cierra bugs:** —
- **Tamaño:** M, dividida en dos PRs: **T21a** backend (`feat/T21-event-chat-reminders`, apilada sobre T20) y **T21b** frontend (`feat/T21b-event-chat-front`, sobre `main`).
- **Spec:** refinada el 2026-10-27 contra el código de la rama de T20 (ver "Implementación").

## Objetivo
Coordinar la quedada ("llego 5 min tarde", "¿hay barro?") y que nadie se olvide de ella.

## Enfoque
- Chat de evento: `Conversation` `GROUP` con `managed_by = EVENT`, sincronizado con las asistentes `GOING` (las de la lista de espera, opcional). Se archiva (solo lectura) 7 días después del evento.
- Recordatorios: job programado (cada 5 min) que envía `EVENT_REMINDER` 24 h y 2 h antes a las `GOING`, de forma idempotente (tabla o columna `reminder_sent_*`). Con varias instancias del backend hará falta ShedLock; con una basta con documentarlo.
- `GET /api/events/{id}.ics` y botón "Añadir al calendario".
- Cancelar o cambiar la hora de un evento → mensaje de sistema en su chat + notificación.

## Historial para quien entra (decisión de Yago, 2026-10-27)
En el chat gestionado de quedada las nuevas participantes ven el **historial completo**; no se aplica el filtro por `joined_at` de los grupos manuales ni la opción "historial visible para nuevas" (ver T12). Test: una usuaria que entra después ve los mensajes anteriores.

## Implementación (refinada)
- **Migración `V16__event_chat_and_reminders.sql`:** columnas `events.reminder_24h_sent_at` y `reminder_2h_sent_at` (sin mapear en la entidad: guardar un evento nunca las toca ni sube `@Version`); índice único parcial `conversations (managed_ref_id) WHERE managed_by = 'EVENT'` (un chat por evento); backfill SQL con un chat por cada evento no cancelado cuyo archivo (fin + 7 días) no ha pasado, con sus `GOING`.
- **`message/service/EventChatService`** (gemelo de `CommunityChatService`): `ensureChat` (perezoso, bajo el bloqueo de la fila del evento, el mismo que usan `join`/`leave`), `addAttendee` / `removeAttendee` (idempotentes, una sentencia), `syncProfile` (título), `eventCancelled` / `eventUpdated` (mensajes de sistema) y `requireCanWrite`. Se llama dentro de la misma transacción desde `EventService` (crear, apuntarse con plaza, salir, promoción desde la lista de espera, editar, cancelar). La lista de espera **no** está en el chat (la spec lo dejaba opcional).
- **Historial completo** para quien entra (decisión de Yago): `historyStart` ya trataba así los grupos gestionados; hay test (`aNewcomer_seesTheWholeHistory`).
- **Archivo sin job:** el chat es de solo lectura si el evento está cancelado o si han pasado 7 días desde que acabó (`ends_at`, o `starts_at` + 12 h sin fin). Se calcula al escribir y al abrir (`EventChatService.readOnlyReason`); el historial sigue legible y las participantes siguen dentro.
- **Escritura:** `MessageService.processIncomingMessage` -> `EventChatService.requireCanWrite` (cancelado/archivado `409`) y límite por usuaria `UserActionLimiter.eventMessage` (`asaltamontes.rate-limit.event-messages-per-minute`, 20).
- **Mensajes de sistema:** `EVENT_CANCELLED {reason}` y `EVENT_UPDATED {changed, startsAt, timeZone}` (solo cuando cambia hora, zona, ciudad o punto de encuentro, igual que la notificación `EVENT_UPDATED` de T18). El cliente compone el texto y da la hora con la zona del evento (D-019).
- **Recordatorios:** `event/maintenance/EventReminderJob` cada 5 min (`@Scheduled`, apagado en tests como el resto). Cada recordatorio se reclama con un único `UPDATE ... WHERE reminder_*_sent_at IS NULL ... RETURNING id` sobre una ventana de 1 h (`(23 h, 24 h]` y `(1 h, 2 h]` antes del inicio): es idempotente, no repite aunque el job corra dos veces, y un evento creado para dentro de 5 h no recibe el de "24 h". Si el job estuvo caído más de 1 h se salta ese recordatorio en vez de mandarlo tarde. Publica `EventReminder` y `NotificationListener` crea `EVENT_REMINDER` (payload: título, `startsAt`, `timeZone`, `hoursBefore`) para las `GOING`, sin actora. Cambiar la hora de inicio re-arma ambos (`resetReminders`).
- **ShedLock:** no hace falta. Con varias instancias el `UPDATE ... RETURNING` decide en la BD quién reclama cada evento, así que no se duplican notificaciones; ShedLock solo evitaría que dos instancias hagan la misma consulta, y no compensa una dependencia nueva.
- **Calendario:** `GET /api/events/{id}.ics` (`EventCalendar`): `text/calendar`, `Content-Disposition: attachment; filename="quedada.ics"` (nombre fijo, nada del usuario en cabeceras), horas en UTC, texto escapado (un título o descripción no puede añadir propiedades) y líneas plegadas a 75 bytes; `STATUS:CANCELLED` si se canceló y `SEQUENCE` = versión. Misma visibilidad que el evento (404).
- **Bloqueos (T13):** como en los grupos y en T20, un bloqueo no saca a nadie del chat de la quedada ni oculta mensajes. Los recordatorios no tienen actora, así que no se filtran por bloqueos; las notificaciones de cancelación/cambio siguen filtradas como en T18.
- **API:** `GET /api/events/{id}/chat` -> `{conversationId, canWrite, readOnlyReason}` (solo `GOING`; el resto, 404; autorrepara la participación); `GET /api/events/{id}.ics`.
- **Fuera de alcance:** que quien sea expulsada de la comunidad salga también de sus quedadas y del chat (BUG-074); fan-out masivo en eventos de 500 plazas (mismo caso que BUG-073).
- **Frontend (T21b):** botón "Chat" y "Añadir al calendario" en el detalle del evento, texto de `EVENT_REMINDER` en las notificaciones y de `EVENT_CANCELLED` / `EVENT_UPDATED` como mensajes de sistema del chat.
