# BUG-064 — Borrar una cuenta falla (o deja ficheros) si la usuaria tiene imágenes subidas

- **Estado:** resuelto
- **Severidad:** baja (ningún endpoint llama hoy a `UserService.deleteUser`)
- **Confianza:** probable (lectura de código; `media.owner_id` es una FK a `users` sin `ON DELETE`)
- **Área:** backend/user + backend/media
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia, junto con el borrado de cuenta de T23)
- **Alcance resuelto:** solo las imágenes (filas y ficheros). El resto de filas de la cuenta se registra en BUG-065.
- **Detectado:** 2026-10-23, al implementar T17c (Claude)

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserService.java` (`deleteUser`) y `db/migration/V7__media.sql` (`fk_media_owner`).

## Qué pasa
`deleteUser` borra solo el perfil y la cuenta. Si la usuaria tiene filas en `media`, la FK `fk_media_owner` impide borrar la cuenta (error 500). Y aunque la FK no existiera, los ficheros de `media_data` quedarían en disco con datos personales (fotos), lo que importa para el derecho de supresión (RGPD) antes de abrir la plataforma.

## Cómo reproducir
Subir una imagen con una usuaria y llamar a `UserService.deleteUser(id)`: `DataIntegrityViolationException`.

## Arreglo propuesto
Al borrar una cuenta: quitar sus referencias (avatar, y portada/imagen de grupo que haya creado si el recurso se conserva), borrar sus medias con `MediaCleanupService` (ficheros + filas, ya existe desde T17c) y después la cuenta. Definir con T23 qué pasa con comunidades y grupos que creó.
