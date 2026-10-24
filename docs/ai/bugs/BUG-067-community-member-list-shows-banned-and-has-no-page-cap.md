# BUG-067 — La lista de miembros muestra baneadas y no tiene tope de tamaño de página

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código; hallazgo del security-reviewer en BUG-066)
- **Área:** backend/community

## Qué pasa
- `CommunityRepository#findMembersExcept` no filtra por `cm.status`: una miembro ve a las usuarias BANNED de la comunidad.
- `GET /api/communities/{id}/members` usa `@PageableDefault(size = 20)` sin tope: con `?size=2000` se vuelca la lista entera.
- `findCommunityBySlug` devuelve `role` también a una usuaria BANNED (inconsistencia; el front pide la lista y recibe 404, que ya maneja).

## Arreglo propuesto
Filtrar BANNED en la consulta, fijar un máximo de página (p. ej. 50) y devolver `role` null a baneadas en el detalle. Decidir con Yago si baneadas deben verse o no.
