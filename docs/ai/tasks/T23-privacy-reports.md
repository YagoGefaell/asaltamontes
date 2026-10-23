# T23 — Privacidad de perfil, denuncias y panel de moderación

- **Estado:** pendiente
- **Fase:** 4 — Seguridad y cuentas
- **Depende de:** T13, T18
- **Rama sugerida:** `feat/T23-privacy-reports`
- **Cierra bugs:** BUG-034
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Cumplir la promesa de espacio seguro: los perfiles privados lo son de verdad, cualquier contenido se puede denunciar y alguien lo revisa.

## Enfoque
- `isPublicProfile = false` → seguir pasa a ser una **solicitud** que se aprueba. Mientras no se apruebe, no se ven las listas de seguidores y seguidos ni la actividad del perfil. Revisar todos los endpoints de `user/`.
- `birthDate` y `phoneNumber` nunca aparecen en DTOs públicos (revisar). Mostrar la edad, si acaso, solo con consentimiento.
- `reports` (reporter, target_type `USER|MESSAGE|POST|COMMENT|EVENT|COMMUNITY`, target_id, reason, details, status, handled_by, resolución). Rate limit por usuaria.
- Panel de administración (`pages/admin/Admin.jsx` y `adminGuard.jsx` existen): cola de denuncias, ver el contenido, acciones (borrar contenido, suspender la cuenta temporal o permanentemente, descartar). Cuenta suspendida → no puede iniciar sesión y se revocan sus sesiones (T06).
- Roles `ADMIN`/`MODERATOR` globales en `UserAccountRole`, protegidos con `@PreAuthorize`.

## Requisito antes de producción: acceso a las imágenes privadas (2026-10-23)
- Las imágenes subidas con T17 se sirven por URL pública (UUID aleatorio, sin autenticación). Con perfiles y comunidades privadas (esta tarea y T19) eso filtra contenido: antes de producción, las imágenes de contenido privado deben servirse con **URLs firmadas con caducidad o `auth_request` de nginx** (decisión técnica a tomar al refinar esta spec), y la retirada de una imagen denunciada debe poder surtir efecto (sin `immutable` en su caché, o con nombre versionado).
- Revisión de privacidad de las imágenes: que su URL no se exponga en DTOs a quien no puede ver el recurso.
