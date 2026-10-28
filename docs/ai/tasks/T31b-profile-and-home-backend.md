# T31b — Backend de perfil ajeno e Inicio

- **Estado:** hecho
- **Depende de:** T31, D-023
- **Rama sugerida:** `feat/T31b-profile-home-backend`
- **Tamaño:** M

## Objetivo
Datos reales para el perfil de otra usuaria y para Inicio, con las reglas de visibilidad de [D-023](../DECISIONS.md).

## Alcance
- `GET /api/users/{username}/events`: quedadas que **organiza** y que quien mira puede ver (públicas, o de comunidades donde es miembro). Paginado. **Nunca** asistencia.
- `GET /api/users/{username}/communities`: solo comunidades **públicas** de esa usuaria. Paginado.
- `GET /api/events?followedBy=me` (filtro «de quienes sigo» para Inicio): quedadas organizadas por seguidas, mismas reglas de visibilidad.
- Bloqueos en cualquier dirección (D-022): 404 uniforme. Usuarias por `username` (D-021).
- `GET /api/users/suggestions` (decisión de Yago, 2026-10-28): «usuarias para seguir», paginado. Candidatas: seguidas de las que sigo y miembros (no baneadas) de mis comunidades; se excluyen yo, las que ya sigo y las bloqueadas en cualquier dirección. Orden fijo: más seguidoras primero. Una sola consulta (+ count). La privacidad del perfil (`isPublicProfile`) no filtra, igual que `GET /api/users/search` (regla existente).
- Contrato de API actualizado.

## Tests requeridos
- No se filtran quedadas ni comunidades privadas a quien no es miembro; no aparece asistencia; bloqueos; paginación; sin N+1.
- Front: pestañas del perfil ajeno (Quedadas · Comunidades) y sección de Inicio con seguidas.
