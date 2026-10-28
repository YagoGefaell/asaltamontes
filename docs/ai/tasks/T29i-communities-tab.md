# T29i — Pestaña Comunidades: «tus comunidades»

- **Estado:** hecho (propuesta de Yago, 2026-10-28)
- **Depende de:** T29h, T31c (backend)

## Reparto
Buscar → descubrir comunidades. Inicio → qué pasa (quedadas, más adelante publicaciones, T22). Comunidades → **tus** comunidades.

## Pantalla
- **Pendiente** (solo si hay algo): solicitudes por aprobar (moderadoras), invitaciones recibidas y solicitudes propias en espera.
- **Tus comunidades**, por actividad: último mensaje del chat con no leídos y próxima quedada.
- Enlace «Descubrir comunidades cerca de ti» → `/search`.
- Sin comunidades: estado vacío con 3–5 sugerencias cercanas (sin GPS: ciudad del perfil) y «Crear comunidad». El `+` de crear sigue en la cabecera.
- Se quita «Tus comunidades» de Inicio y la hoja de filtros ciudad/provincia de esta pantalla.
- Descartado: feed de actividad y descubrir por categorías.

## Backend (T31c)
`/api/communities/me` con último mensaje, no leídos y próxima quedada; endpoint que agrupe lo pendiente (solicitudes, invitaciones).

## Contrato T31c (implementado)
- `GET /api/communities/me` (paginado, `size` ≤ 50): las mismas comunidades de antes, ahora ordenadas por **actividad** (último mensaje del chat; si nadie ha escrito, el día que se unió) salvo `sort` explícito (`activity|name|memberCount|createdAt`). Cada elemento añade `chat { conversationId, lastMessage, lastSender, lastMessageAt, unreadCount }` y `nextEvent { id, title, startsAt, timezone }` (la quedada programada más cercana que no ha empezado; `null` si no hay). Si el último mensaje es de alguien con bloqueo en cualquier dirección (D-022), `lastMessage` y `lastSender` van a `null` pero `lastMessageAt` se mantiene: el front muestra «Mensaje de una usuaria bloqueada». Número de sentencias fijo (página + bloqueos + próximas quedadas + count), con test.
- `GET /api/communities/me/pending`: `{ toApprove: [{ community, pendingCount, latestRequestAt }], mine: [{ id, community, requestedAt }] }`. `toApprove` solo para propietaria/moderadora no expulsada; no cuenta solicitudes de usuarias con bloqueo en cualquier dirección con ella y **no identifica** a las solicitantes (para eso, la lista de `/requests`, que ya exige permisos). `mine` son solo sus solicitudes. Máximo 50 por lista, sin paginar.
- **Invitaciones recibidas:** no existen en el modelo (las invitaciones de T25 son códigos de registro, no de comunidad), así que el endpoint no las incluye. Si se añade esa función, se amplía el contrato con `invitations`.
- Sugerencias del estado vacío: el front usa `GET /api/communities?city=<ciudad del perfil>` y descarta las que ya son suyas (sin cambios de backend).
