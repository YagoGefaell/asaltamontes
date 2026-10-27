# T19 — Gestión y moderación de comunidades + comunidades privadas

- **Estado:** pendiente (T19a backend hecho en esta PR; queda T19b frontend)
- **Fase:** 2b — Comunidades
- **Depende de:** T14, T18
- **Rama sugerida:** `feat/T19-community-moderation`
- **Cierra bugs:** —
- **Tamaño:** L → dividida en **T19a** (backend) y **T19b** (frontend)
- **Spec:** refinada el 2026-10-27 (ver al final).

## Objetivo
Que cada comunidad (grupo local de trail) pueda gestionarse sola: editar datos, nombrar moderadoras, silenciar o expulsar, y ser **privada**, con solicitudes de acceso.

## Decisión de Yago sobre expulsadas y silenciadas (2026-10-24, BUG-067)
- **Las baneadas no aparecen** en la lista normal de miembros **ni cuentan** en el recuento de miembros (`memberCount`; el contador denormalizado y `CommunityMemberCounterMaintenanceService` deben contar solo `ACTIVE` y `MUTED`).
- **Sección aparte "Expulsadas"**: solo la ven la propietaria y las moderadoras. Muestra quién, cuándo, el motivo y quién la expulsó (sale del registro `community_moderation_log`) y un botón **Readmitir**, que queda también en el registro. Es un endpoint propio (p. ej. `GET /api/communities/{id}/banned`), paginado, con `size` máximo 50, y 404 uniforme para quien no modera.
- **La baneada ve la comunidad como una no miembro**: no ve la lista de miembros, no tiene rol, no puede volver a unirse (ni por solicitud en comunidades privadas) y el intento de unirse devuelve un mensaje neutro (sin revelar el motivo ni la expulsión; p. ej. "No se puede completar esta acción"). La ficha pública sigue siendo la de un no miembro y `myRole`/`myStatus` no delatan el baneo.
- **Las silenciadas (`MUTED`) siguen en la lista normal** y cuentan en el recuento. La marca de silenciada solo la ve la moderación: el DTO de miembros incluye `status` únicamente cuando quien consulta es propietaria o moderadora.
- **Paginación**: todos los listados paginados de esta tarea (miembros, expulsadas, solicitudes, registro de moderación) tienen `size` máximo 50.
- Encaja con BUG-066 (la lista de miembros exige ser miembro no baneada): la baneada recibe la misma respuesta que una no miembro.
- Tests añadidos: baneada ausente de la lista y del recuento, `GET .../banned` solo para moderación (404 al resto), readmitir (vuelve a la lista y al recuento, queda en el registro), baneada no puede unirse y ve la comunidad como no miembro, silenciada visible en la lista y `status` solo para moderación, `size > 50` se recorta.
- Requisitos que salieron de la revisión de seguridad de BUG-067: expulsar debe decrementar `memberCount` y readmitir incrementarlo en la misma transacción (con test); no se puede expulsar a la propietaria; tests de moderadora (ve `status`) y de moderadora baneada (404 en `members`).

## Enfoque
- **Identificadores (D-021, T30a):** el `slug` sirve para URLs legibles y compartibles (`GET /api/communities/{slug}`, rutas del front) y **puede cambiar** si se renombra la comunidad; las acciones de la API (`join`, `leave`, `members`, moderación, `DELETE`) usan el `public_id` UUID de la comunidad, que no cambia. `PATCH` por `{slug}` o por UUID; los enlaces antiguos por slug no se garantizan tras un renombrado salvo que se guarde el histórico de slugs.
- Usar los campos ya existentes `CommunityRole.MODERATOR` y `MemberStatus.MUTED/BANNED`, que hoy no se usan.
- `PATCH /api/communities/{slug}` (nombre, descripción, imagen vía T17, ciudad/provincia, visibilidad `PUBLIC|PRIVATE`).
- Miembros: cambiar el rol, silenciar o expulsar (con motivo) y transferir la propiedad. Las baneadas no pueden volver a unirse.
- Comunidad privada: `join` crea una solicitud `PENDING` → la moderación acepta o rechaza → notificación (T18). Los no miembros solo ven el nombre, la imagen, la descripción y el número de miembros.
- Registro de moderación (`community_moderation_log`) con quién, qué y cuándo.
- Ubicación de la comunidad (ciudad/provincia) y búsqueda por ubicación.
- Frontend: completar `CommunitySettingsMenu.jsx` (las rutas de ajustes de comunidad ya existen).

## Refinamiento (2026-10-27) — división y decisiones

### T19a — backend (hecho)
- Migración `V13__community_moderation.sql`: `communities.visibility|city|province`, tablas `community_moderation_log` y `community_join_requests` (la fila existe solo mientras está pendiente), índice único parcial de dedupe para la notificación `COMMUNITY_JOIN_REQUEST`.
- API (todo en `docs/api-contract-v1.json`): `PATCH /api/communities/{slug|uuid}`; `PUT .../members/{username}/role`; `POST .../members/{username}/{mute|unmute|ban|unban}`; `POST .../transfer-ownership/{username}`; `GET .../banned`, `GET .../requests`, `GET .../moderation-log`; `POST .../requests/{username}/{approve|reject}`; `join` devuelve 202 en comunidades privadas; `GET /api/communities?city=&province=`.
- Los tres listados nuevos (expulsadas, solicitudes, registro) usan **cursores opacos** (`before`/`nextBefore`, `limit` ≤ 50) que apuntan a una fila de esa comunidad (400 si no).
- Reglas: solo propietaria y moderadoras actúan; el resto (también una moderadora baneada) recibe el 404 de "comunidad inexistente". La propietaria cambia roles y transfiere; una moderadora solo modera a miembros; nadie expulsa o silencia a la propietaria (409) ni a sí misma (400). Expulsar quita el rol de moderadora. Expulsar/readmitir/aceptar mueven `memberCount` en la misma transacción, con la fila del miembro bloqueada (dos moderadoras a la vez cuentan una sola vez).
- La propiedad se determina por rol `OWNER` (no por `created_by`), para poder transferirla.
- El slug **no cambia** al renombrar (los enlaces compartidos siguen funcionando).
- Notificaciones: `COMMUNITY_JOIN_REQUEST` (a propietaria y moderadoras), `COMMUNITY_JOIN_APPROVED` y `COMMUNITY_JOIN_REJECTED` (la de rechazo no lleva actor). Los bloqueos se respetan a través de `NotificationService`.
- Silenciar solo marca a la usuaria (visible para la moderación); su efecto en el chat de comunidad llega con T20.
- Fuera de alcance: expulsar no la saca de eventos/chats de la comunidad (T20); las imágenes de comunidades privadas se sirven por URL pública (T23); la ubicación es texto libre (ciudad/provincia), sin geocodificar.

### T19b — frontend (pendiente)
- Completar `CommunitySettingsMenu.jsx`: editar datos, visibilidad, ubicación; miembros con roles/silenciar/expulsar (con motivo); sección "Expulsadas" con "Readmitir"; solicitudes pendientes (aceptar/rechazar); registro de moderación; transferir propiedad.
- Texto de las notificaciones `COMMUNITY_JOIN_*` (payload `communityName`, target `COMMUNITY` con el slug).
- Botón "Solicitar unirse" / "Solicitud enviada" según `visibility` y `joinRequested`; buscador por ciudad/provincia.

## Decisiones de Yago (2026-10-28) — implementadas en `feat/yago-decisions-t12-t13-t18-t19`
- **Cooldown tras rechazar una solicitud de unión:** 7 días por defecto, configurable con `asaltamontes.community.join-request-cooldown` (Duration: `7d`, `PT12H`; `0` lo desactiva). Mientras dure, `POST /api/communities/{id}/join` a esa comunidad privada responde `429` con `Retry-After` (segundos) y un mensaje con los días que faltan. Se calcula con la última entrada `REQUEST_REJECTED` del registro de moderación (índice parcial nuevo), sin tabla aparte. No afecta a otras comunidades ni a otras usuarias.
- **Jerarquía al readmitir:** una moderadora **no puede readmitir** (unban) a quien expulsó la propietaria (`403` "Solo la propietaria puede readmitir a quien ella expulsó"); la propietaria puede readmitir a cualquiera, y una moderadora puede readmitir a quien expulsó otra moderadora. Para ello `community_members` guarda `banned_by` (FK a `users`, `ON DELETE SET NULL`) y `banned_by_role` (`OWNER` | `MODERATOR`, el rol **en ese momento**: sobrevive a una transferencia de propiedad o a que la autora borre su cuenta). Se limpian al readmitir.
- **Migración `V14__moderation_hierarchy_and_join_cooldown.sql`:** añade las dos columnas, rellena las expulsiones existentes desde el último `BAN` del registro (rol `OWNER` si su autora es hoy la propietaria; en cualquier otro caso, o sin entrada en el registro, `MODERATOR`, la lectura permisiva) y crea el índice de rechazos. Probada sobre una BD con datos: `ModerationHierarchyMigrationIT`.
- Tests: `CommunityModerationIT` (`afterARejection_...`, `aModerator_cannotReadmitWhoTheOwnerExpelled_...`).
