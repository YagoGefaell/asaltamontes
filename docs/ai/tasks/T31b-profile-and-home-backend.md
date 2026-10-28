# T31b — Backend de perfil ajeno e Inicio

- **Estado:** pendiente
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
- Contrato de API actualizado.

## Tests requeridos
- No se filtran quedadas ni comunidades privadas a quien no es miembro; no aparece asistencia; bloqueos; paginación; sin N+1.
- Front: pestañas del perfil ajeno (Quedadas · Comunidades) y sección de Inicio con seguidas.
