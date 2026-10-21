# BUG-007 — Dejar de seguir decrementa contadores aunque no se siguiera

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/FollowService.java` → `unfollowUser()`; `UserFollowRepository.deleteByFollowerIdAndFollowingId` devuelve `void`

## Qué pasa
Se decrementan `followingCount`/`followersCount` aunque el `DELETE` no haya borrado ninguna fila. Un doble clic o una petición repetida descuadra los contadores (protegidos solo con `> 0`).

## Cómo reproducir
Seguir a B, luego `DELETE /api/users/follow/B` dos veces: el contador de seguidores de B baja de más si tenía otros.

## Arreglo propuesto
Que el `DELETE` devuelva `int` y decrementar solo si es 1. Test IT.
