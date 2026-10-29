# BUG-079 — `/api/communities/{slug}/requests` lista solicitudes de usuarias bloqueadas

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado
- **Área:** backend/community
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-28, revisión de seguridad de la PR #116 (T29i/T31c)

## Qué pasa
`GET /api/communities/me/pending` excluye las solicitudes de usuarias con bloqueo en cualquier dirección y su `pendingCount`, pero el listado `GET /api/communities/{slug}/requests` sigue incluyéndolas: dos vistas incoherentes para la moderadora (D-022).

## Arreglo propuesto
Aplicar el mismo filtro de bloqueos al listado de solicitudes, con test. Además, desempate por `c.id` en `GET /api/communities/me` con `sort=name|memberCount` (orden estable en empates) y test de que una moderadora expulsada no ve `toApprove`.
