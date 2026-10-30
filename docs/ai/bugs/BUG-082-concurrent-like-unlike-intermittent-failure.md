# BUG-082 — `PostInteractionIT.concurrentLikesFromManyUsersAndRepeatedFromOne` falla de forma intermitente

- **Estado:** resuelto
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

## Causa y arreglo
Los logs del run fallido ya no están (la re-ejecución los sobrescribió), así que la causa se dedujo del código. `insertLike` usa `ON CONFLICT DO NOTHING` y devuelve 0 tanto si ya existía el like como si la sentencia lo rechazó; `PostService.like` distinguía ambos casos con `isLikedBy` y lanzaba `NotFoundException` (404) si no estaba. Bajo READ COMMITTED, un `unlike` concurrente de la misma usuaria puede confirmarse entre el insert (0 por conflicto) y `isLikedBy` (false), y el `like` respondía 404 en vez de 200. Arreglo: reintentar el insert (hasta 3 veces) antes de concluir que fue rechazado; el insert vuelve a comprobar publicación viva y no baneo. No se encontró relación con BUG-081 (no hay deadlock: los bloqueos son insert/delete de la fila y luego `UPDATE` de `posts`, sin ciclo). El test endurece la segunda fase (60 peticiones en vez de 12).
