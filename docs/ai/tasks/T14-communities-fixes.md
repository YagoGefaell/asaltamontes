# T14 — Corrección de comunidades: slug, ordenación, errores y paginación

- **Estado:** hecho
- **Fase:** 2a — Comunidades base
- **Depende de:** T02, T04
- **Rama sugerida:** `fix/T14-communities-fixes`
- **Cierra bugs:** BUG-012, BUG-013, BUG-014, BUG-015, BUG-035, BUG-045, BUG-046, BUG-047
- **Tamaño:** M

## División en sub-PRs (2026-10-21)
- **T14a — backend** (hecho): migración V4 (nombres normalizados y únicos sin distinguir mayúsculas, `slug` con backfill, índice trigram), `GET /api/communities/{slug}` (con respaldo por nombre para los enlaces antiguos), lista blanca de `sort` (`SortWhitelist`), 404/409/403 en unirse y en las operaciones de la propietaria, `POST` → 201 con `Location` y `CommunityDetailsDto`, `?query=` con comodines escapados, mensajes en español. Cierra BUG-012, BUG-014, BUG-015, BUG-046, BUG-047 y la parte backend de BUG-013.
- **T14b — frontend** (hecho): rutas `/communities/:slug` y enlaces con `communityPath` (las URLs antiguas por nombre redirigen a la del slug), "Cargar más comunidades", buscador con debounce, rollback del botón de unirse en el padre y reintento tras un error (`useOptimisticToggle`). Cierra BUG-013, BUG-035 y BUG-045.

## Objetivo
Dejar el módulo de comunidades robusto antes de construir eventos y moderación encima.

## Contexto
- `CommunityController`: `@PageableDefault(sort = "name")` en `/me` y `/{id}/members`, cuyas consultas usan los alias `m`/`cm` (entidad `CommunityMember`, sin `name`).
- Las comunidades se identifican en la URL por su **nombre** (`/communities/:communityName`), sin `encodeURIComponent`.
- `isOwner` devuelve `boolean` primitivo desde una consulta que puede no tener filas.
- `joinCommunity`: `getReferenceById` sin comprobar si existe; los duplicados se detectan por violación de unique; no tiene en cuenta `MemberStatus.BANNED`.
- `leaveCommunity` (propietaria) → `IllegalArgumentException` (T04 la convierte en el código correcto).
- Frontend `communities.service.js#getCommunitiesRequest` devuelve solo `response.content` (primera página).

## Alcance
### Backend
- Migración: `communities.slug` (único, `lower`, `[a-z0-9-]`, generado desde el nombre con sufijo si colisiona; backfill de las existentes).
- `GET /api/communities/{slug}` sustituye a la búsqueda por nombre. Se mantiene la ruta antigua por nombre durante una versión, marcada deprecated, solo si el frontend desplegado la necesita.
- Lista blanca de ordenaciones por endpoint (`name`, `memberCount`, `createdAt` para comunidades; `username`, `joinedAt` para miembros). Ordenar por algo fuera de la lista → 400. Implementar con un `SortValidator` reutilizable o mapeando a la propiedad correcta con el alias de la consulta.
- `isOwner` → `Optional`/`exists` (comunidad inexistente → 404).
- `join`: 404 si no existe, 409 si ya es miembro, 403 si está `BANNED`.
- `create`: el slug devuelto en la respuesta 201 (`Location` + body `CommunityDetailsDto`).
- Búsqueda: `GET /api/communities?query=` por nombre (trigram, índice como en T05).

### Frontend
- Rutas `/communities/:slug`; los enlaces usan `slug`.
- Listado con scroll infinito o "cargar más", usando `totalPages`/`last`.
- Buscador de comunidades en la página de comunidades.

## Criterios de aceptación
- [ ] Una comunidad llamada "Trail Vigo / Sur #1" se crea, se abre y se comparte por URL sin errores.
- [ ] `/api/communities/me` y `/members` responden 200 con la ordenación por defecto; un `sort` inválido → 400.
- [ ] Unirse a una comunidad inexistente → 404; dos veces → 409; estando baneada → 403.
- [ ] Con 50 comunidades se pueden ver todas desde el frontend.

## Tests requeridos
- IT de cada criterio. Unit test del generador de slugs (acentos, símbolos, colisiones).
