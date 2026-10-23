# BUG-062 — `EventSummaryDto` no incluye latitud/longitud: la vista de mapa de la lista no puede dibujar chinchetas

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/events (hueco de contrato)
- **Tarea que lo resuelve:** T16c (vista de mapa de la lista, frontend) tras un cambio pequeño de backend
- **Detectado:** 2026-10-22, al implementar T16b (Claude)

## Dónde
- `backend/src/main/java/io/github/yagogefaell/asaltamontes/event/dto/EventSummaryDto.java`
- Proyección en `EventSearchRepositoryImpl` / `EventService` que construye el summary.

## Qué pasa
`GET /api/events` (también con bbox `minLat/maxLat/minLng/maxLng`) y `GET /api/communities/{slug}/events` devuelven `EventSummaryDto`, que **no tiene `latitude` ni `longitude`** (sí las tiene `EventDetailsDto`). El filtro por bbox funciona, pero el frontend no sabe dónde colocar cada quedada en el mapa. La única alternativa es pedir el detalle de cada evento (hasta 50 peticiones por movimiento del mapa), que no es aceptable.

## Cómo reproducir
`GET /api/events?minLat=40&maxLat=41&minLng=-4&maxLng=-3` → los elementos de `content` no traen coordenadas.

## Arreglo propuesto
Añadir `Double latitude, Double longitude` al final de `EventSummaryDto` (y a la proyección), actualizar `docs/api-contract-v1.json` y los tests de la API. Después, T16c: vista "Mapa" en `/events` con `getEventsInBoundsRequest` (bbox del área visible, debounce de `moveend`, máx. 50 chinchetas, mismos filtros que la lista).
