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

## Enfoque
- **Identificadores (D-021, T30a):** el `slug` sirve para URLs legibles y compartibles (`GET /api/communities/{slug}`, rutas del front) y **puede cambiar** si se renombra la comunidad; las acciones de la API (`join`, `leave`, `members`, moderación, `DELETE`) usan el `public_id` UUID de la comunidad, que no cambia. `PATCH` por `{slug}` o por UUID; los enlaces antiguos por slug no se garantizan tras un renombrado salvo que se guarde el histórico de slugs.
- Usar los campos ya existentes `CommunityRole.MODERATOR` y `MemberStatus.MUTED/BANNED`, que hoy no se usan.
- `PATCH /api/communities/{slug}` (nombre, descripción, imagen vía T17, ciudad/provincia, visibilidad `PUBLIC|PRIVATE`).
- Miembros: cambiar el rol, silenciar o expulsar (con motivo) y transferir la propiedad. Las baneadas no pueden volver a unirse.
- Comunidad privada: `join` crea una solicitud `PENDING` → la moderación acepta o rechaza → notificación (T18). Los no miembros solo ven el nombre, la imagen, la descripción y el número de miembros.
- Registro de moderación (`community_moderation_log`) con quién, qué y cuándo.
- Ubicación de la comunidad (ciudad/provincia) y búsqueda por ubicación.
- Frontend: completar `CommunitySettingsMenu.jsx` (las rutas de ajustes de comunidad ya existen).
