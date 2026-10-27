# BUG-067 — La lista de miembros muestra baneadas y no tiene tope de tamaño de página

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código; hallazgo del security-reviewer en BUG-066)
- **Área:** backend/community

## Qué pasa
- `CommunityRepository#findMembersExcept` no filtra por `cm.status`: una miembro ve a las usuarias BANNED de la comunidad.
- `GET /api/communities/{id}/members` usa `@PageableDefault(size = 20)` sin tope: con `?size=2000` se vuelca la lista entera.
- `findCommunityBySlug` devuelve `role` también a una usuaria BANNED (inconsistencia; el front pide la lista y recibe 404, que ya maneja).

## Arreglo propuesto
Filtrar BANNED en la consulta, fijar un máximo de página (p. ej. 50) y devolver `role` null a baneadas en el detalle. Decidir con Yago si baneadas deben verse o no.

## Resolución (decisión de Yago, 2026-10-24; ver T19)
- Las baneadas no aparecen en la lista de miembros ni cuentan en `memberCount` (el recuento nocturno cuenta solo `ACTIVE`/`MUTED`).
- La baneada ve la comunidad como una no miembro: 404 en `members`, `role` null en detalle, listado y búsqueda, no aparece en `/me`, `leave` da 404 sin borrar su fila (no puede "limpiar" el baneo) y `join` da 403 "No puedes unirte a esta comunidad" (no revela el motivo).
- Las silenciadas siguen en la lista y cuentan; el campo `status` del DTO de miembros solo se envía a propietaria/moderadoras (ausente para el resto).
- Tope de página: `spring.data.web.pageable.max-page-size=50` recorta (no rechaza) `size` en todos los endpoints con `Pageable`; los cursores de chat (`limit`) bajan de 100 a 50. Se recorta en lugar de dar 400 porque es lo que ya hacía el código vecino (`EventService`, `MessageService`).
- La sección "Expulsadas" con readmitir queda para T19.
