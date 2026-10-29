# BUG-081 — 500 intermitente al publicar en el perfil (visto en `RateLimitIT.posting_isLimitedPerUser`)

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable
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
