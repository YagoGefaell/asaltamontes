# T15 — Eventos (quedadas, entrenos, carreras): modelo y API

- **Estado:** hecho
- **Fase:** 3 — Quedadas / eventos
- **Depende de:** T14
- **Rama sugerida:** `feat/T15-events-api` (dividir: `T15a` modelo + CRUD, `T15b` inscripción + lista de espera, `T15c` búsqueda y filtros)
- **Cierra bugs:** —
- **Tamaño:** L

## División en sub-PRs (2026-10-22)
- **T15a — modelo + CRUD** (hecho): migración V5 (`events`, `event_attendees`), paquete `event/`, `POST /api/events` (la organizadora queda `GOING`; con `communityId`, solo OWNER/MODERATOR), `GET /api/events/{id}` (`myStatus`, `canEdit`, `status` con `FINISHED` derivado), `PATCH` (organizadora o moderación; no por debajo de las inscritas; no si está cancelado), `POST …/cancel` (idempotente, publica `EventCancelled`), `GET /api/events` (próximos visibles, paginado), visibilidad `COMMUNITY` (404 para quien no es miembro o está baneada), `@Version` → 409.
  - Diferencias con este diseño: `event_attendees` usa `id` + unique `(event_id, user_id)` como `community_members` (no PK compuesta); `status` solo guarda `SCHEDULED`/`CANCELLED`. Sin `FINISHED` guardado; un evento sin `ends_at` cuenta como terminado 12 h después de empezar. Visibilidad y comunidad no se cambian con `PATCH`. Los índices de ciudad y bbox van con sus filtros en T15c.
- **T15b — inscripción y lista de espera** (hecho): `join`/`leave` con la fila del evento bloqueada (`SELECT … FOR UPDATE`), `attendee_count` solo con `UPDATE` masivos (`updatable = false` en la entidad: una edición cargada antes no devuelve el número antiguo), lista de espera con posiciones 1..n sin huecos, promoción al salir una `GOING` o al subir las plazas (`AttendeePromoted`), idempotencia, la organizadora no puede salir (409), `GET …/attendees?status=`. IT con 100 inscripciones simultáneas y 10 plazas.
- **T15c — búsqueda y filtros** (hecho): filtros de `GET /api/events` (`from`, `to`, `type`, `level`, `communityId`, `city` sin distinguir mayúsculas, `minDistance`/`maxDistance`, bbox completo o nada, `organizer=me`, `attending=me` con `GOING` y `WAITLIST`), `GET /api/communities/{slug}/events` (controlador propio en `event/`), `400` para filtros inválidos. Consulta construida con Criteria API (`EventSearchRepositoryImpl`): solo entran en el SQL los filtros presentes; la versión JPQL con `:p IS NULL OR …` fallaba en PostgreSQL ("could not determine data type of parameter") y no podría usar índices. La regla de visibilidad `COMMUNITY` va siempre en el mismo `WHERE`. Migración V6: `(lower(city), starts_at)` parcial y `(latitude, longitude)` parcial.
  - EXPLAIN (PG 17 desechable, 200 000 eventos, 5 000 usuarias, 300 comunidades; todas con índice y < 1 ms salvo el `COUNT` de una ciudad con 3080 resultados, 6,5 ms): listado por defecto `idx_events_starts_at` (0,2 ms); ciudad `idx_events_city_starts_at` (0,2 ms); bbox pequeño `idx_events_coordinates` por bitmap (0,6 ms); comunidad `idx_events_community_starts_at` (0,1 ms); `organizer=me` `idx_events_organizer` (0,3 ms); `attending=me` `idx_event_attendees_user_status` (0,4 ms); tipo + nivel + distancia + ventana de fechas: recorre `idx_events_starts_at` en la ventana y filtra (0,2 ms; sin índice propio, no compensa con el volumen esperado).
  - Pendiente fuera de T15: frontend (T16).

## Objetivo
La funcionalidad diferencial de la app: organizar quedadas para correr en montaña. Una usuaria (o una comunidad) crea un evento con lugar, hora, distancia, desnivel, nivel y plazas; otras se apuntan, con lista de espera si se llena.

## Modelo (nuevo paquete `event/`)
**`events`**
| columna | tipo | notas |
|---|---|---|
| id | bigint identity | |
| title | varchar(120) | not null |
| description | text | ≤ 4000 |
| type | varchar(20) | `MEETUP` (quedada) · `TRAINING` · `RACE` · `TRIP` |
| status | varchar(20) | `SCHEDULED` · `CANCELLED` · `FINISHED` (derivable, ver notas) |
| starts_at | timestamptz | not null, futuro al crear |
| ends_at | timestamptz | nullable, > starts_at |
| timezone | varchar(40) | por defecto `Europe/Madrid` (se muestra la hora local del evento) |
| meeting_point_name | varchar(200) | texto libre ("Parking del Monte Galiñeiro") |
| latitude / longitude | double precision | nullable, rango válido |
| city | varchar(80) | para filtrar sin coordenadas |
| distance_km | numeric(5,1) | nullable |
| elevation_gain_m | integer | nullable |
| level | varchar(20) | reutiliza `RunningLevel` |
| capacity | integer | nullable = sin límite; 1–500 |
| attendee_count | integer | desnormalizado, not null default 0 |
| visibility | varchar(20) | `PUBLIC` · `COMMUNITY` (solo miembros) |
| community_id | FK nullable | si la organiza una comunidad |
| organizer_id | FK not null | |
| image_url | varchar | se gestiona por T17 |
| created_at / updated_at / cancelled_at | timestamptz | |
| version | bigint | bloqueo optimista para ediciones |

Índices: `(starts_at)`, `(community_id, starts_at)`, `(organizer_id)`, `(city, starts_at)`, y `(latitude, longitude)` (bbox simple; PostGIS no hace falta todavía).

**`event_attendees`**: `event_id`, `user_id`, `status` (`GOING` · `WAITLIST` · `CANCELLED`), `waitlist_position`, `joined_at`, `updated_at`; PK `(event_id, user_id)`; índice `(user_id, status)`.

## API
- `POST /api/events` (crear; si `communityId`, requiere ser OWNER/MODERATOR de la comunidad; la organizadora queda inscrita como `GOING`).
- `GET /api/events/{id}` → detalle + `myStatus` + `canEdit`.
- `PATCH /api/events/{id}` (organizadora o moderación de la comunidad). Reducir `capacity` por debajo de `attendee_count` → 409.
- `POST /api/events/{id}/cancel` → `status = CANCELLED` + motivo (las notificaciones a asistentes llegan en T18; dejar el evento de dominio `EventCancelled` publicado).
- `POST /api/events/{id}/join` → `GOING` si hay plaza, si no `WAITLIST` con posición.
- `POST /api/events/{id}/leave` → si era `GOING` y hay lista de espera, **promociona** a la primera (evento de dominio `AttendeePromoted`).
- `GET /api/events/{id}/attendees?status=GOING` paginado.
- `GET /api/events` filtros: `from`, `to` (por defecto: desde ahora), `type`, `level`, `communityId`, `city`, `minDistance`, `maxDistance`, bbox (`minLat,maxLat,minLng,maxLng`), `organizer=me`, `attending=me`; orden `starts_at ASC`; paginado.
- `GET /api/communities/{slug}/events` (próximos).
- Visibilidad `COMMUNITY`: solo miembros activos ven el evento y pueden apuntarse (404 en otro caso).

## Concurrencia (crítico)
- `join`/`leave` dentro de una transacción con `SELECT … FOR UPDATE` sobre la fila del evento (`@Lock(PESSIMISTIC_WRITE)`), o con un `UPDATE events SET attendee_count = attendee_count + 1 WHERE id = ? AND (capacity IS NULL OR attendee_count < capacity)` condicional y comprobando las filas afectadas. **Nunca** más inscritas `GOING` que `capacity`.
- Idempotencia: apuntarse dos veces devuelve el estado actual (200), no un error.

## Fuera de alcance
- Frontend → T16. Chat del evento y recordatorios → T21. Notificaciones → T18. Imagen → T17. GPX → después de la demo.

## Criterios de aceptación
- [ ] 100 peticiones `join` concurrentes a un evento con `capacity = 10` → exactamente 10 `GOING` y 90 `WAITLIST` con posiciones 1..90 sin huecos.
- [ ] Si una `GOING` se va, la primera de la lista de espera pasa a `GOING`.
- [ ] Un evento `COMMUNITY` no aparece ni se puede abrir para quien no es miembro.
- [ ] No se puede crear un evento en el pasado ni editar uno cancelado.
- [ ] Filtros combinados devuelven resultados correctos y paginados; las consultas usan índice.

## Tests requeridos
- IT de cada endpoint y permiso; IT de concurrencia (join masivo, join/leave intercalados); unit tests de validación.
