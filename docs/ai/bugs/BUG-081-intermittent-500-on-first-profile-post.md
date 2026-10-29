# BUG-081 — 500 intermitente al publicar en el perfil (visto en `RateLimitIT.posting_isLimitedPerUser`)

- **Estado:** abierto (no reproducible; investigado 2026-10-29, ver «Investigación»)
- **Severidad:** media
- **Confianza:** especulativa (sin traza ni reproducción)
- **Área:** backend/post
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-29, `./mvnw verify` completo en la rama de #132 (agente); Yago pide investigarlo (un 500 intermitente no puede llegar a producción)

## Dónde
`post/service/PostService.java` (`create`, que termina con `get(user, post.getPublicId())` dentro de la misma transacción) y `RateLimitIT.posting_isLimitedPerUser`.

## Qué pasa
En la suite completa, la primera publicación de perfil (`POST /api/posts` con solo `body`) de la usuaria de prueba devolvió **500** una vez. Repetida sola, `RateLimitIT` pasó (12/12) y `PostIT` también (13/13). La causa no está identificada: no se conservó la traza.

## Cómo reproducir
Sin reproducción fiable aún. Intentar: `./mvnw verify` completo varias veces (y con `-Dsurefire.rerunFailingTestsCount=0`), guardando `target/surefire-reports` y los logs de la aplicación del test fallido.

## Hipótesis a descartar (por orden)
1. Carrera con un listener `@Async` (como BUG-077): una notificación o un contador se escribe en otra transacción mientras se crea la publicación.
2. Estado compartido entre clases de test (contexto de Spring reutilizado, límite `posts-per-day` ligado a un usuario/clave de una clase anterior, usuarias sin `user_profiles` aún).
3. `get(...)` justo después de `saveAndFlush` en la misma transacción: que la consulta de lectura (`PostQueryRepository.one`) no vea la fila o falle al mapear (`createdAt` Instant, `public_id` UUID v7, fotos), lanzando una excepción no controlada en lugar de 404.
4. Generación de `public_id` (UUID v7) con colisión/orden en el mismo milisegundo (poco probable).

## Arreglo propuesto
Reproducir con la traza completa; corregir la causa. Añadir un handler/log que muestre la causa de cualquier 500 en tests (la traza debe quedar en el informe de surefire). No silenciar el test ni marcarlo como flaky.

## Investigación (2026-10-29, agente)
**Reproducción:** `./mvnw -B verify` completo 3 veces seguidas desde `origin/main` (337 tests unitarios + 49 IT por pasada, en la Pi sin otra suite en paralelo): **0 fallos y 0 errores en las 3**, `RateLimitIT` y `PostIT` incluidos. El único `ERROR ... Unexpected error` de cada log es el esperado de `GET /actuator/health` con cabecera `X-Request-Id` con salto de línea (test del filtro de request id), no de publicaciones.

**Hipótesis revisadas en el código:**
1. *Listener `@Async` / otra transacción*: descartada. `PostService.create` no publica ningún evento (el único `publishEvent` de `post/` es `PostCommented`, en comentar). Los `@Scheduled` (cron nocturnos y `EventReminderJob` cada 5 min) tocan otras tablas.
2. *Estado compartido entre clases*: improbable. `RateLimitIT` tiene su propio contexto (`@TestPropertySource`) pero el limitador es en memoria por contexto y las claves llevan el id de usuaria, que es nueva en cada test; la BD de Testcontainers se comparte sin colisiones (datos únicos).
3. *`get(...)` tras `saveAndFlush` en la misma transacción*: sin fallo en 3 pasadas; `PostQueryRepository.one` es una proyección JPQL que lee de BD, no del contexto de persistencia, y `like_count`/`comment_count` tienen valor por defecto en BD. Sin indicio de fallo.
4. *`UuidV7`*: `next()` es `synchronized` y estrictamente creciente por JVM; una colisión exigiría 62 bits aleatorios iguales, y además daría 409 (`DataIntegrityViolationException`), no 500.
5. *(nueva, no comprobada)* Contención de recursos: el 500 original se vio con `./mvnw verify` mientras otro agente podía estar usando la Pi. Un `CannotCreateTransactionException`/timeout de Hikari o un contenedor Postgres lento bajo presión de memoria acabaría en el `handleGeneric` (500). Encaja con «una sola vez, no repetible».

**Conclusión:** sin traza ni reproducción no hay causa que arreglar y no se inventa un arreglo. El `GlobalExceptionHandler.handleGeneric` ya registra con `log.error` la traza completa de cualquier 500, así que basta conservar el log de la pasada fallida (`target/surefire-reports/*-output.txt` o la salida de Maven).

**Si vuelve a ocurrir:** guardar el log completo de `mvnw verify` y buscar `Unexpected error on POST /api/posts` (la causa va justo debajo); anotar si había otra suite o carga alta (`uptime`, `free -m`). Con esa traza se reabre con confianza «probable». Mientras tanto queda abierto y sin prioridad.
