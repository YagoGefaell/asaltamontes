# BUG-066 — Cualquier usuaria autenticada puede listar los miembros de una comunidad

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código; hallazgo del security-reviewer en T30a, anterior a esa PR)
- **Área:** backend/community
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia, tras decidir la regla de producto)
- **Detectado:** 2026-10-24, revisión de seguridad de T30a

## Dónde
`CommunityController#getCommunityMembers` y `CommunityService#getCommunityMembersExcept`.

## Qué pasa
`GET /api/communities/{id}/members` no comprueba que quien pregunta sea miembro: devuelve username, nombre, foto y rol de los miembros a cualquier usuaria autenticada que conozca el id de la comunidad (que sale en el listado público).

## Cómo reproducir
Con una usuaria que no pertenece a la comunidad, llamar al endpoint: responde 200 con la lista.

## Arreglo propuesto
Decisión de producto de Yago: ¿la lista de miembros es pública o solo para miembros? Si es solo para miembros, exigir membresía no baneada (403/404 si no) y añadir test.
