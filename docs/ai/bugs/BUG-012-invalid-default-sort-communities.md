# BUG-012 — La ordenación por defecto `name` rompe `/communities/me` y `/communities/{id}/members`

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (reproducido en T01: `GET /api/communities/me` → 500)
- **Área:** backend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/community/controller/CommunityController.java` (`@PageableDefault(sort = "name")`), `CommunityMemberRepository.findCommunitiesByUserId`, `CommunityRepository.findMembersExcept`

## Qué pasa
Spring Data añade `order by <alias>.name` usando el alias de la primera entidad del `FROM` (`m`/`cm`, que son `CommunityMember`). `CommunityMember` no tiene `name` → error de Hibernate → 500. Además, cualquier `?sort=` arbitrario que envíe el cliente también provoca un 500.

**Desde T04:** un `?sort=` con una propiedad desconocida devuelve **400** ("Parámetro de ordenación no válido") en lugar de 500, y queda un `WARN` en el log. Por tanto, `/communities/me` y `/communities/{id}/members` con su ordenación por defecto ahora fallan con **400** en vez de 500: sigue siendo este bug (del servidor, no de la cliente). El frontend actual no usa esos endpoints.

## Cómo reproducir
`GET /api/communities/1/members` sin parámetros, con al menos un miembro.

## Arreglo propuesto
Lista blanca de ordenaciones mapeadas a las propiedades correctas; `sort` inválido → 400 (T14).
