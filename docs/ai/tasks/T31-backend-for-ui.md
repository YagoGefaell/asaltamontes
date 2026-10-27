# T31 — Soporte de backend para la UI

- **Estado:** pendiente
- **Fase:** Transversal (UI)
- **Depende de:** T15 (eventos), T29d/T29e (mergeadas)
- **Rama sugerida:** `feat/T31-backend-for-ui`
- **Cierra bugs:** —
- **Tamaño:** M
- **Prioridad:** la siguiente en cuanto haya hueco de PRs, por delante de T29f y T29h (decisión de Yago, 2026-10-27).

## Objetivo
Dar al backend lo que la UI de T29d/T29e ya espera y hoy simula o recorta: búsqueda de texto de quedadas, vista previa de asistentes en la tarjeta de quedada y ciudad en el listado de comunidades.

## Alcance
### Backend
1. **`q` en `GET /api/events`**: texto libre (título, y ciudad/lugar si existe) sin distinguir mayúsculas ni acentos, combinable con los filtros actuales y con la paginación por cursor. Longitud mínima 2 y máxima 80; escapar `%` y `_` del `LIKE`. Límite de tamaño de página ya existente.
2. **`attendeePreview` en `EventSummaryDto`**: hasta 3 asistentes (los más antiguos confirmados, excluyendo bloqueadas en cualquier dirección, D-022) con `username`, `publicId` y `avatar.thumb`. **Sin N+1**: una sola consulta adicional por página (por ejemplo `row_number() over (partition by event_id)` o consulta por lista de ids) y test que lo compruebe (contador de sentencias o equivalente). También `attendeeCount` si no existe ya.
3. **`city` en el listado de comunidades** (`CommunitySummaryDto`), y filtro opcional `city` si es trivial.
4. Contrato: actualizar `docs/api-contract-v1.json` y los DTOs/tests de contrato.

### Frontend (mínimo)
- Usar `q` en el buscador único (pestaña Quedadas), `attendeePreview` en `EventCard` (avatares apilados) y `city` en las filas de comunidad. Quitar los apaños temporales.

## Fuera de alcance
- Geolocalización y "cerca de mí" con GPS. De momento las sugerencias "cerca de mí" salen sin GPS, a partir de la ciudad del perfil y de las de mis comunidades (ver T29d). La geolocalización es opcional, posterior y solo al pulsar «Cerca de mí».

## Criterios de aceptación
- [ ] `GET /api/events?q=…` filtra por texto, es insensible a mayúsculas y acentos y respeta cursor y filtros.
- [ ] `EventSummaryDto.attendeePreview` trae ≤ 3 asistentes con `username`, `publicId` y `avatar.thumb`, sin N+1 ni bloqueadas.
- [ ] El listado de comunidades incluye `city`.
- [ ] Contrato de API actualizado.

## Tests requeridos
- Integración: `q` (acentos, mayúsculas, caracteres `%`/`_`, longitud inválida → 400), `attendeePreview` (orden, máximo 3, bloqueadas excluidas, número de consultas constante con 1 y con 20 eventos), `city`.
- Frontend: la tarjeta pinta los avatares; el buscador envía `q`.
