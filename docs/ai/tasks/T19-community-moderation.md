# T19 — Gestión y moderación de comunidades + comunidades privadas

- **Estado:** pendiente
- **Fase:** 2b — Comunidades
- **Depende de:** T14, T18
- **Rama sugerida:** `feat/T19-community-moderation`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

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
