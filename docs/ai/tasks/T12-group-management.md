# T12 — Gestión de grupos de chat

- **Estado:** pendiente
- **Fase:** 1 — Chat
- **Depende de:** T09
- **Rama sugerida:** `feat/T12-group-management`
- **Cierra bugs:** —
- **Tamaño:** M

## Objetivo
Hoy un grupo se crea y ya no se puede modificar. Para organizar entrenos hace falta añadir y quitar gente, salir, renombrar y tener administradoras. Además, esta base la reutilizarán los chats de comunidad (T20) y de evento (T21).

## Contexto
- `Conversation` (`type = GROUP`, `groupName`, `groupImage`), `ConversationParticipant` (sin rol).
- `POST /api/conversations/group` crea el grupo y la creadora es una participante más.
- Frontend: `pages/messages/components/GroupCreatorPanel.jsx`.

## Alcance
### Backend
- Migración: `conversation_participants.role` (`ADMIN` | `MEMBER`, por defecto `MEMBER`), la creadora pasa a `ADMIN` (backfill: la participante con menor `joined_at` de cada grupo). `conversations.created_by_id` (nullable para los existentes, backfill igual). `conversations.managed_by` (`NONE` | `COMMUNITY` | `EVENT`, por defecto `NONE`) + `managed_ref_id`, que ya dejan preparados T20/T21: los grupos gestionados no permiten añadir ni quitar miembros manualmente.
- Endpoints (solo `GROUP`; las `ADMIN` gestionan, salvo salir):
  - `GET /api/conversations/{id}` → detalle (nombre, imagen, participantes paginados con rol).
  - `PATCH /api/conversations/{id}` → nombre/imagen.
  - `POST /api/conversations/{id}/participants` `{ usernames: [] }` → añadir (respetando el límite de T09 y los bloqueos de T13 cuando exista).
  - `DELETE /api/conversations/{id}/participants/{username}` → expulsar.
  - `POST /api/conversations/{id}/leave` → salir; si sale la última admin, se promociona a la participante más antigua; si no queda nadie, se borra la conversación.
  - `PATCH /api/conversations/{id}/participants/{username}` `{ role }` → promocionar o degradar.
- **Mensajes de sistema**: `messages.type` (`USER` | `SYSTEM`) y `sender_id` nullable para los de sistema ("Ana ha añadido a Marta"). Se emiten por WS como cualquier mensaje.
- Las expulsadas dejan de recibir mensajes por WS (el envío consulta los participantes actuales) y pierden acceso al historial.

### Frontend
- Panel "Info del grupo" en el chat: participantes, añadir (buscador de usuarias reutilizando el de `Search`), expulsar, cambiar rol, renombrar, salir.
- Renderizar los mensajes de sistema centrados y en gris.

## Criterios de aceptación
- [ ] Solo las admins pueden añadir, expulsar, renombrar y cambiar roles (403 en los demás casos).
- [ ] Una expulsada no recibe mensajes nuevos ni puede leer el historial.
- [ ] Salir siendo la última admin promociona a otra participante.
- [ ] Cada cambio genera un mensaje de sistema visible para todas.

## Tests requeridos
- IT por endpoint y caso de permiso; IT de "última admin sale"; IT de que una expulsada no recibe por WS.
