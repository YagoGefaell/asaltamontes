# BUG-082 — `PostInteractionIT.concurrentLikesFromManyUsersAndRepeatedFromOne` falla de forma intermitente

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable
- **Área:** backend/post
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-29, CI de la PR #148 (cambio solo de frontend), run 40941834201

## Dónde
`PostInteractionIT.java:113`: en la segunda fase del test, 12 peticiones concurrentes (6 hilos) de `like`/`unlike` de la **misma** usuaria sobre la misma publicación; se espera `200` en todas y alguna devolvió otro estado. Código implicado: `PostService` (like/unlike y contador `like_count`).

## Qué pasa
En `main` el test pasó en local (337 ITs) y en la CI de #139/#141, pero falló una vez en la CI de #148, que no toca backend. Es el segundo fallo intermitente en un camino de escritura de posts tras BUG-081 (500 al publicar): podrían compartir causa (carrera, bloqueo o violación de unicidad traducida a 500 en vez de ser idempotente).

## Cómo investigar
- Descargar el log de la ejecución (`gh run view 40941834201 --log-failed`) y buscar el estado devuelto y `Unexpected error on POST/DELETE /api/posts/.../likes`.
- Repetir el test en bucle (`-Dit.test=PostInteractionIT`) con carga en paralelo.
- Revisar que like/unlike sean idempotentes bajo concurrencia (p. ej. `INSERT ... ON CONFLICT DO NOTHING`, y borrado sin error si no existe) y que el contador se actualice sin deadlocks.
- Relacionado: BUG-081.
