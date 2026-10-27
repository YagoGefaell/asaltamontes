# T20 — Chat de comunidad

- **Estado:** hecho
- **Fase:** 2b — Comunidades
- **Depende de:** T12, T14
- **Rama sugerida:** `feat/T20-community-chat`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** refinada el 2026-10-27 contra el código de `main` (ver "Implementación"). Tamaño final M: una sola PR (backend + un botón y un interruptor en el front).

## Objetivo
Cada comunidad tiene un chat de grupo propio, sincronizado con su lista de miembros.

## Enfoque
- Reutilizar `Conversation` de tipo `GROUP` con `managed_by = COMMUNITY` (campos preparados en T12). No se pueden añadir ni quitar miembros a mano: entrar o salir de la comunidad sincroniza la conversación.
- Creación perezosa (al primer acceso) o en la creación de la comunidad, más un backfill para las existentes.
- Las silenciadas (`MUTED`) pueden leer pero no escribir; las baneadas pierden el acceso.
- Posibilidad de que la moderación desactive el chat. Mensajes fijados (opcional).
- Para comunidades grandes: la entrega por WS a N miembros no debe hacer N consultas (resolver ids en una sola consulta; vigilar con T28).

## Historial para quien entra (decisión de Yago, 2026-10-27)
En el chat gestionado de comunidad las nuevas participantes ven el **historial completo**; no se aplica el filtro por `joined_at` de los grupos manuales ni la opción "historial visible para nuevas" (ver T12). Test: una usuaria que entra después ve los mensajes anteriores.

## Implementación (refinada)
- **Migración `V15__community_chat.sql`:** `communities.chat_enabled boolean NOT NULL DEFAULT true`; índice único parcial `conversations (managed_ref_id) WHERE managed_by = 'COMMUNITY'` (un chat por comunidad); backfill SQL con un chat por cada comunidad existente y sus miembros no expulsados (ACTIVE y MUTED).
- **`message/service/CommunityChatService`** (única pieza nueva de lógica): `ensureChat` (perezoso, bajo bloqueo de la fila de la comunidad), `addMember` / `removeMember` (idempotentes, una sentencia), `syncProfile` (nombre e imagen de la comunidad), `deleteChat` y `requireCanWrite`. Se llama dentro de la misma transacción desde `CommunityService` (crear, unirse, salir, borrar) y `CommunityModerationService` (aceptar solicitud, expulsar, readmitir, editar). Mensajes de sistema: ninguno (en comunidades grandes serían ruido y N escrituras).
- **Participantes:** todas `MEMBER`; el chat gestionado rechaza añadir, expulsar, cambiar roles (ya existía), renombrar y **salir** (`409`): se sale de la comunidad. Expulsada o salida: se borra su participación y recibe `{conversationId, reason}` por `/user/queue/conversations`.
- **Historial completo** para quien entra (decisión de Yago): `historyStart` ya trataba los grupos gestionados sin filtro; ahora hay test.
- **Escritura** (`MessageService.processIncomingMessage` -> `requireCanWrite`): silenciada `403`, chat desactivado `409`, y límite por usuaria `UserActionLimiter.communityMessage` (`asaltamontes.rate-limit.community-messages-per-minute`, 20) porque cada mensaje llega a todas las miembros. Fuera del chat de comunidad no cambia nada.
- **API:** `GET /api/communities/{communityId}/chat` -> `{conversationId, enabled, canWrite}` (miembra no expulsada; autorrepara la participación); `PATCH /api/communities/{ref}` acepta `chatEnabled` (propietaria y moderadoras).
- **Bloqueos:** como en los grupos (T13), un bloqueo no saca a nadie del chat de comunidad ni oculta mensajes; entrar en una comunidad no depende de los bloqueos.
- **Entrega por WS:** sin cambios, una consulta de ids por mensaje (`findUserIdsByConversationPublicId`); el `UPDATE` de no leídos es una sola sentencia. Vigilar con T28 en comunidades de miles de miembros.
- **Frontend:** botón "Chat" en el perfil de la comunidad (solo miembros) que abre la conversación en `/messages`; interruptor "Activar/Desactivar el chat" en los ajustes de la propietaria. Silenciada o chat apagado: el servidor rechaza y el aviso sale con el mecanismo de errores existente.
- **Fuera de alcance / pendiente:** mensajes fijados (opcional en la spec), aviso visual previo de "solo lectura" en el input, y ajustes de moderación para moderadoras (el interruptor vive en la pantalla de la propietaria).
