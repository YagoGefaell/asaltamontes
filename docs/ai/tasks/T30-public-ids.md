# T30 — Identificadores públicos (UUID v7) y cursores opacos

- **Estado:** pendiente
- **Fase:** Transversal (seguridad)
- **Depende de:** —
- **Rama sugerida:** `feat/T30-public-ids` (dividir si pasa de M: `T30a` backend + test de arquitectura, `T30b` frontend, `T30c` cursores)
- **Cierra bugs:** —
- **Tamaño:** L
- **Cuándo:** lo antes posible y **antes de T18 (notificaciones), T12 (grupos) y T22 (feed)**, que crean recursos nuevos expuestos. Si se hace después, esos recursos tienen que nacer ya con `public_id` (D-021).

## Objetivo
Aplicar D-021 a lo que ya existe: que ningún id interno `bigint` salga de la API, para que nada sea enumerable ni revele volúmenes, manteniendo la PK `bigint` por rendimiento.

## Contexto
- Ya se exponen por clave natural: usuarias (`username`), comunidades (`slug`). Las imágenes (T17) ya usan UUID.
- Se exponen con id numérico (revisar en el código actual): eventos (`/api/events/{id}`), conversaciones (`/api/conversations/{id}`), mensajes (ids y cursor de paginación del historial), asistentes y lo que haya aparecido desde entonces. Hacer inventario con `grep` en controladores, DTOs, mensajes STOMP y frontend.

## Alcance
### BD (una migración Flyway)
- Columna `public_id uuid NOT NULL UNIQUE` en cada tabla expuesta, con backfill para las filas existentes (los datos son desechables, D-014, pero la migración no debe fallar con datos).
- Índice único (es el que se usa para buscar desde la API).

### Backend
- Generación de UUID **v7** en Java al crear la entidad (librería pequeña y mantenida o implementación propia probada; comprobar con context7 si la versión de Hibernate ya trae un generador v7). Sin cambiar la PK ni las relaciones.
- Repositorios: `findByPublicId`. Controladores y servicios aceptan y devuelven `public_id`; el `Long id` queda interno.
- DTOs y payloads de WebSocket con `id` = `public_id` (UUID). Las referencias entre recursos también (`conversationId`, `eventId`…).
- **Cursores opacos** en toda paginación por cursor (historial del chat, lista de conversaciones, eventos…): token codificado (p. ej. base64url de `{valor de orden, public_id}`), validado al recibirlo; un cursor manipulado → 400.
- Los ids de path inválidos (no UUID) → 400 con `ProblemDetail`, nunca 500.
- **Test de arquitectura** (ArchUnit u otro equivalente) que haga **fallar la CI** si un record de `dto/` de respuesta tiene un campo `Long`/`long` llamado `id` o `*Id` que referencie una entidad expuesta. Lista de excepciones explícita y comentada si hiciera falta.

### Frontend
- Rutas y servicios con el UUID (`/events/:publicId`…), estado y comparaciones por `public_id`.
- Enlaces compartibles de eventos con el UUID.

### Docs
- `docs/api-contract-v1.json` actualizado.
- Regla en `CLAUDE.md` (ya añadida en la PR de D-021) y nota en el README del backend sobre cómo añadir `public_id` a una entidad nueva.

## Fuera de alcance
- Cambiar las PK a UUID (descartado en D-021).
- Usuarias y comunidades: siguen por `username` y `slug`.

## Criterios de aceptación
- [ ] Ninguna respuesta de la API ni ningún mensaje de WebSocket contiene un id interno numérico de un recurso expuesto (comprobado con el test de arquitectura y con una revisión de los payloads).
- [ ] `/api/events/123` → 400; `/api/events/<uuid inexistente>` → 404; `/api/events/<uuid sin permiso>` → 404 o 403 según la regla de visibilidad actual.
- [ ] Los cursores no se pueden manipular para saltar a otras conversaciones ni para inferir volúmenes.
- [ ] Los enlaces de eventos compartidos funcionan con el UUID.
- [ ] Rendimiento: las consultas por `public_id` usan el índice único (EXPLAIN en la PR).

## Tests requeridos
- Test de arquitectura en la CI.
- IT por recurso: búsqueda por UUID, 400 con id numérico o malformado, 404 con UUID inexistente.
- IT de cursores: paginación completa sin solapes y cursor manipulado → 400.
- Unit test del generador: los UUID son v7 y crecientes en el tiempo.
