# BUG-070 — Dejar de seguir sin seguir antes descuenta los contadores

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** probable
- **Área:** backend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-27, al implementar T13 (lectura de código)

## Dónde
`user/service/FollowService.java#unfollowUser`

## Qué pasa
`DELETE /api/users/follow/{username}` borra la fila (si existe) y decrementa siempre `following_count` y `followers_count`, aunque no hubiera follow. Repetir la llamada, o llamarla sobre alguien a quien no se sigue, deja los contadores por debajo de la realidad (el decremento solo se detiene en 0). `FollowCounterMaintenanceService` lo corrige a posteriori.

## Cómo reproducir
A sigue a B y a C. Llamar dos veces a `DELETE /follow/B`: `followingCount` de A baja a 0 estando en 1 follow real (C).

## Arreglo propuesto
Como en `BlockService.removeFollow` (T13): `deleteByFollowerIdAndFollowingId` ya devuelve el número de filas borradas; decrementar solo si es mayor que 0. Test que falla sin el arreglo.
