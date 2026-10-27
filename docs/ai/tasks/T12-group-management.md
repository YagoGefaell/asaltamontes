# T12 — Gestión de grupos de chat

- **Estado:** pendiente
- **Fase:** 1 — Chat
- **Depende de:** T09 (hecho), T30a (hecho en la rama `feat/T30a-public-ids`; ids públicos, 404 uniforme)
- **Rama sugerida:** `feat/T12-group-management`
- **Cierra bugs:** BUG-056, BUG-057 (parte)
- **Tamaño:** M (1 PR grande; si el diff se dispara, cortar en T12a = migración + endpoints + mensajes de sistema y T12b = frontend)
- **Sub-PRs:** T12a (backend: migración V10, `GroupService`, endpoints, mensajes de sistema, filtro de historial por `joined_at`; hecha) y T12b (frontend, apilada sobre T12a). El `Estado` pasa a `hecho` en T12b. BUG-056 (frontend) se cierra en T12b; BUG-057 (longitud de la imagen) queda cubierto en T12a por `@Size(255)` en el PATCH.
- **Spec:** refinada el 2026-10-24 contra el código de `feat/T30a-public-ids`.

## Objetivo
Hoy un grupo se crea y ya no se puede modificar. Para organizar entrenos hace falta añadir y quitar gente, salir, renombrar y tener administradoras. Además, esta base la reutilizarán los chats de comunidad (T20) y de evento (T21).

## Contexto (estado actual del código)
- `message/domain/Conversation extends PublicIdEntity` (`type` = `ChatType.GROUP|INDIVIDUAL`, `groupName`, `groupImage`, `groupMediaId`, `lastMessage*`, `participants` con `cascade ALL` y `orphanRemoval`). `ConversationParticipant` **no** tiene `public_id` (no se expone) ni rol; solo `unreadCount` y `joinedAt`, con único `(conversation_id, user_id)`.
- `Message extends PublicIdEntity` con `sender` **obligatorio** (`nullable = false`) y `MessageDto(UUID id, UUID conversationId, String senderUsername, String content, LocalDateTime sentAt)`.
- `MessageService`: `createGroupConversation` (límite `asaltamontes.chat.max-group-size`, por defecto 100; vincula la imagen con `MediaReferenceService.link(..., MediaPurpose.GROUP_IMAGE, ...)`), `processIncomingMessage` (404 uniforme si no es participante, `updateLastMessage`, `incrementUnreadExceptSender`, evento `MessageSentEvent`), historial por cursor de `public_id`.
- `ChatMessageBroadcaster` reparte tras el commit a los participantes **actuales** (`findUserIdsByConversationPublicId`) por `/user/queue/messages`, identificando por id de usuaria. Por eso una expulsada deja de recibir sin tocar el broadcaster.
- `ConversationController` (`/api/conversations`): `private`, `group`, listado, `/{conversationId}/messages`, `/{conversationId}/read`. El `{conversationId}` ya es el UUID público.
- Frontend: `pages/messages/components/{GroupCreatorPanel,ChatPanel,ConversationsPanel}.jsx`, `features/messages/{services/messages.service.js,utils/mergeMessages.js,hooks/useWebSocket.js,components/conversations.provider.jsx}`.

## Alcance

### Backend
**Migración `V<n>__group_management.sql`** (skill `/new-migration`; no editar migraciones mergeadas):
- `conversation_participants.role varchar NOT NULL DEFAULT 'MEMBER'` (`ADMIN` | `MEMBER`), con backfill: `ADMIN` para la participante de menor `joined_at` de cada `GROUP`. Índice parcial `(conversation_id) WHERE role = 'ADMIN'`.
- `conversations.created_by_id bigint NULL` FK `user_accounts` `ON DELETE SET NULL` (nulo en los existentes salvo backfill igual que el rol).
- `conversations.managed_by varchar NOT NULL DEFAULT 'NONE'` (`NONE` | `COMMUNITY` | `EVENT`) y `managed_ref_id bigint NULL` (interno; **no sale en ningún DTO**, T20/T21 lo rellenan). Un grupo gestionado rechaza añadir, expulsar y cambiar roles a mano (409 `Este grupo se gestiona automáticamente`) pero permite salir si T20/T21 lo deciden; en T12 solo se deja la columna y la comprobación.
- `messages.type varchar NOT NULL DEFAULT 'USER'` (`USER` | `SYSTEM`), `messages.sender_id` pasa a **nullable** con `CHECK (type = 'SYSTEM' OR sender_id IS NOT NULL)`, y `messages.system_payload jsonb NULL` (`{ "action": "MEMBER_ADDED", "actor": "ana", "targets": ["marta"] }`).

**Dominio y DTOs** (`message/`): `ParticipantRole` (enum), `MessageType`; `ConversationParticipant.role`; `Conversation.createdBy`, `managedBy`, `managedRefId`; `Message.type`, `systemPayload`, `sender` nullable. `MessageDto` añade `MessageType type` y `Map<String,Object> systemPayload` (nulo en los de usuaria); `senderUsername` es `null` en los de sistema. Revisar **todas** las consultas JPQL de `MessageRepository` y `ConversationRepository` que hacen join con `sender` / `lastSender` (pasan a `LEFT JOIN`) y `ChatSummaryDto.lastMessageSenderName` (nulo en sistema).
- Nuevos DTO: `GroupDetailsDto(UUID id, String name, String imageUrl, UUID imageMediaId?, int participantCount, ParticipantRole myRole, Page<ParticipantDto>)`, `ParticipantDto(String username, String fullName, String profilePictureUrl, ParticipantRole role, LocalDateTime joinedAt)`, `AddParticipantsDto(List<String> usernames)`, `UpdateGroupDto`, `UpdateRoleDto(ParticipantRole role)`. Sin ningún `Long` ni `ConversationParticipant.id`.

**Endpoints** (todos solo `GROUP`; en un chat individual responden 404; se implementan en `ConversationController` + un `GroupService` nuevo para no engordar `MessageService`):

| Método y ruta | Quién | Efecto |
|---|---|---|
| `GET /api/conversations/{id}` | participante | detalle; participantes paginados (`page`, `size` por defecto 20, **máximo 50**; orden: admins primero, luego `joined_at`) |
| `PATCH /api/conversations/{id}` | admin | `groupName` / `groupImage` / `groupMediaId` |
| `POST /api/conversations/{id}/participants` `{usernames}` | admin | añadir; respeta `max-group-size` y, cuando exista T13, los bloqueos |
| `DELETE /api/conversations/{id}/participants/{username}` | admin | expulsar (no a sí misma: para eso `leave`) |
| `PATCH /api/conversations/{id}/participants/{username}` `{role}` | admin | promover o degradar |
| `POST /api/conversations/{id}/leave` | participante | salir |

- **404 uniforme**: conversación inexistente, ajena (no es participante) o no `GROUP` → el mismo 404 `Chat no encontrado`, reutilizando el helper `participantConversationId`. Participante que no es admin → 403. Un `username` objetivo que no es participante → 404 `Participante no encontrada`.
- `PATCH` de grupo (D-020): campo **ausente** = sin cambios; `groupImage`/`groupMediaId` con `null` explícito = quitar la imagen (libera la referencia de media); `groupName` no es opcional, así que `null` o vacío es 400. Usar el mismo mecanismo que adopte el PATCH de eventos (`JsonNullable` o mapa de campos presentes); no inventar otro.
- Añadir: los usernames se normalizan y deduplican como en `createGroupConversation`; ya participantes se ignoran sin error; ninguno existente → 400; usernames inexistentes → 404 sin añadir ninguna (todo o nada, transacción única). El tope de tamaño se comprueba contra el recuento real **bloqueando la fila de la conversación** (`PESSIMISTIC_WRITE`) para que dos admins concurrentes no lo superen. Máximo 50 usernames por petición.
- Salir: si sale la última admin se promociona a la participante más antigua (`joined_at`, desempate por `id`); si no queda nadie, se borra la conversación con sus mensajes y se libera su imagen. Misma operación bajo bloqueo de fila para que dos admins saliendo a la vez no dejen el grupo sin admin.
- Expulsar o salir **borra** el `ConversationParticipant`: `participantConversationId` y `markConversationAsRead` ya devuelven 404 y la ex-participante pierde historial y WS sin cambios en el broadcaster.
- **Mensajes de sistema**: el servicio crea un `Message` `SYSTEM` por acción (añadidas, expulsada, salida, cambio de nombre, imagen, rol), con `system_payload` estructurado. Se guarda también un `content` neutro de respaldo en español. Actualizan `lastMessage`/orden de la lista y suman no leídos a las demás participantes (parametrizar `incrementUnreadExceptSender` por el id de quien actúa), y se emiten con `MessageSentEvent` por el broadcaster existente. El mensaje de "te han expulsado" no llega a la expulsada (ya no participa): en su lugar recibe `{conversationId, reason: "REMOVED"}` por `/user/queue/conversations` (`convertAndSendToUser`, id de usuaria) para que el front quite el chat de su lista.
- Notificaciones: publicar el evento de dominio `AddedToGroup` (lo consume T18, ver su spec); T12 no depende de T18 para funcionar.
- Límites de longitud del nombre, y formato de imagen, los mismos de `CreateGroupDto`.

### Frontend
- Panel "Info del grupo" (`pages/messages/components/GroupInfoPanel.jsx`) abierto desde la cabecera de `ChatPanel`: participantes paginados con "Cargar más", rol visible, y para admins: añadir (buscador de usuarias reutilizando el de `Search`), expulsar, cambiar rol, renombrar y cambiar imagen (flujo de `GROUP_IMAGE` de T17b); para todas: salir con confirmación. Textos en español.
- `messages.service.js`: funciones nuevas para cada endpoint; `mergeMessages` y `ChatPanel` renderizan `type === "SYSTEM"` centrados y en gris, componiendo el texto en español desde `systemPayload` (no desde `content`), y sin avatar ni nombre de remitente.
- `conversations.provider.jsx`: escuchar `/user/queue/conversations` y retirar el chat (y cerrar el panel si estaba abierto) en `REMOVED`.
- Los controles de admin se ocultan a quien no lo es; la autorización real es la del servidor.

## Fuera de alcance
- Bloqueos (T13): solo el punto de extensión al añadir.
- Chats de comunidad y evento (T20, T21): aquí solo `managed_by`/`managed_ref_id` y su comprobación.
- Notificaciones (T18): aquí solo se publica `AddedToGroup`.
- Invitaciones con enlace, aprobación de entrada y silenciar un grupo.
- Cursores opacos (T30c); el cursor del historial no cambia.
- Que borrar una cuenta (`UserService#deleteUser`) aplique la regla "última admin" de los grupos: si no queda cubierto, registrarlo como `BUG-xxx` en la PR.

## Criterios de aceptación
- [ ] Solo las admins pueden añadir, expulsar, renombrar, cambiar imagen y cambiar roles (403 a participantes no admin; 404 idéntico a ajenas e inexistentes, y a chats individuales).
- [ ] Una expulsada no recibe mensajes nuevos por WS, no puede leer el historial ni marcar como leído (404) y su lista de chats se actualiza al instante.
- [ ] Salir siendo la última admin promociona a la participante más antigua; salir siendo la última participante borra la conversación.
- [ ] Dos admins añadiendo a la vez no superan `max-group-size`; dos admins saliendo a la vez no dejan el grupo sin admin.
- [ ] Cada cambio genera un mensaje de sistema visible para todas las participantes, con `senderUsername` nulo y `type = SYSTEM`; la lista de chats y los no leídos se actualizan.
- [ ] `PATCH` con campo ausente no cambia nada; `groupImage: null` quita la imagen; `groupName: null` es 400.
- [ ] El listado de participantes respeta `size` máximo 50.
- [ ] Ningún DTO ni mensaje WS expone un id `bigint` (`PublicIdArchitectureTest` pasa); los grupos y mensajes existentes siguen funcionando tras la migración.
- [ ] El grupo `managed_by <> NONE` rechaza gestión manual de miembros.

## Tests requeridos
- **IT** por endpoint y caso de permiso (admin, miembro no admin, ajena, inexistente, chat individual); añadir con duplicados, inexistentes y tope; salir (no admin, última admin, última participante); cambio de rol; `PATCH` con ausente/`null`.
- **IT de concurrencia** (patrón de los tests existentes con hilos): tope de tamaño y última admin.
- **IT WS** (`StompTestClient`): la expulsada no recibe `/user/queue/messages` y sí `REMOVED`; las demás reciben el mensaje de sistema.
- **IT de migración**: backfill de rol y `created_by_id` sobre datos preexistentes (patrón de `infrastructure/db`); consultas del historial y la lista con mensajes `SYSTEM` (join con `sender` nulo).
- **Frontend (Vitest)**: servicio, render de mensaje de sistema, panel (permisos visibles según rol, añadir/expulsar/salir), provider ante `REMOVED`.

## Notas y riesgos
- **Decisión de Yago (2026-10-27) — historial para quien entra:** en los grupos creados a mano, la nueva participante ve solo los mensajes **desde que entra** (como WhatsApp): hay que filtrar por `joined_at` en `findLatest/findBefore/findAfter` y en el cursor (T30c). Opción de administradora del grupo **"historial visible para nuevas"** (por grupo, desactivada por defecto): si está activa, las que entren después ven el historial completo. Los chats gestionados de comunidad (T20) y de quedada (T21) **no** usan este filtro: historial completo. Implica una columna de grupo (p. ej. `conversations.history_visible_to_new boolean`) y que el filtro se aplique solo a conversaciones de tipo grupo manual. Tests: la nueva no ve lo anterior; con la opción activa sí; expulsada y readmitida (¿`joined_at` nuevo? sí, ve desde la readmisión).
- `sender_id` nullable afecta a cualquier código que asuma remitente; buscar usos de `getSender()` antes de implementar.
- No bloquear el broadcaster: la lista de destinatarias se calcula tras el commit.
