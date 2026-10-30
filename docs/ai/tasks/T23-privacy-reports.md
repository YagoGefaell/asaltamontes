# T23 — Privacidad de perfil, denuncias y panel de moderación

- **Estado:** pendiente (spec refinada; se divide en T23a–T23d). **T23a hecho** (cierra BUG-034); **T23b hecho** (backend; el front de «Denunciar» queda como sub-tarea **T23b2**); **T23c1 hecho** (cola, detalle, descartar, borrar contenido); **T23c2 hecho** (suspensión de cuentas); queda T23c3 (front del panel) y T23d.
- **Fase:** 4 — Seguridad y cuentas
- **Depende de:** T13, T18
- **Rama sugerida:** `feat/T23-privacy-reports`
- **Cierra bugs:** BUG-034
- **Tamaño:** L
- **Spec:** refinada (2026-10-29), mirando el código de `user/`: `UserProfile.isPublicProfile`, `UserFollow`, `UserAccountRole {USER, ADMIN}`; `birthDate` y `phoneNumber` solo salen en `UserSelfDto` (el propio perfil), no en DTOs ajenos.

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

## Refinado (2026-10-29)

### División
| PR | Contenido | Depende de |
|---|---|---|
| **T23a** | Perfil privado de verdad: solicitudes de seguimiento (BUG-034) | — |
| **T23b** | Denuncias: modelo, endpoint para denunciar, rate limit | T22a (para `POST`/`COMMENT`; el resto de tipos ya) |
| **T23c** | Panel de administración: cola, acciones, suspensión de cuentas, rol `MODERATOR`. Se parte en **T23c1** (backend: rol, cola, detalle, descartar, borrar contenido, auditoría), **T23c2** (backend: suspensión de cuentas) y **T23c3** (front `pages/admin`). BUG-065/068 quedan en T24c (borrar cuenta) | T23b |
| **T23d** | Imágenes privadas: URLs firmadas o `auth_request` (requisito antes de producción) | T19, T22a |

### T23a — Perfil privado
- Migración: `follow_requests(id, follower_id, followed_id, created_at, UNIQUE(follower_id, followed_id))` o columna `status` en `user_follows` (`ACCEPTED|PENDING`); elegir al implementar la que mantenga los contadores atómicos de T05/BUG-070 (los contadores solo cuentan `ACCEPTED`).
- `follow` a perfil privado crea solicitud (`202`); `DELETE` la cancela. `GET /api/users/me/follow-requests` (paginado), `POST .../{username}/approve|reject`. Notificaciones `FOLLOW_REQUEST` y `FOLLOW_REQUEST_APPROVED` (T18, con cooldown).
- Sin aprobación no se ven: listas de seguidores/seguidos, quedadas organizadas ni comunidades (D-023), publicaciones (T22). Sí se ven nombre de usuario, avatar, bio y recuentos (como Instagram). Revisar todos los endpoints de `user/` y `event/` por `username`.
- Pasar un perfil de privado a público acepta las solicitudes pendientes; de público a privado no expulsa a las seguidoras actuales.
- Front: botón «Solicitar», «Solicitado», pantalla de solicitudes (dentro de Notificaciones), perfil con candado.

**Implementación (T23a, V20):** tabla aparte `follow_requests` (en vez de `status` en `user_follow`), así `user_follow` sigue significando «aceptada», los contadores y todas las consultas de seguimiento no cambian. Aprobar borra la solicitud y solo si la ha borrado esa llamada inserta el seguimiento y cuenta (a prueba de doble clic y de carreras). `ProfileAccess.canViewContent` es la regla única para listas, quedadas y comunidades; las publicaciones la aplican en JPQL (`PostQueryRepository.PROFILE_VISIBLE`). `GET /api/users/{u}` añade `isFollowRequested` y `canViewContent`. Quien no puede ver recibe páginas vacías (no 403). Rechazar no notifica. Bloquear borra las solicitudes entre ambas.

### T23b — Denuncias
- `reports(id, public_id, reporter_id, target_type, target_public_id, reason enum, details ≤ 1000, status OPEN|DISMISSED|ACTIONED, handled_by, handled_at, resolution)`; única denuncia abierta por (reporter, target). Límite 20/día por usuaria (429 + `Retry-After`).
- `POST /api/reports`. No se revela a la denunciada. El tipo `MESSAGE` guarda una copia del texto en el momento de la denuncia (el original puede borrarse).
- Front: opción «Denunciar» en menús de usuaria, mensaje, quedada, comunidad (y publicación/comentario tras T22).

**Implementación (T23b, V22):** `POST /api/reports` con `{targetType, target, reason, details}`; `target` es el `username` (USER), el `slug` (COMMUNITY) o el uuid público (resto, D-021). Motivos: SPAM, HARASSMENT, HATE, INAPPROPRIATE, DANGEROUS, FAKE_ACCOUNT, OTHER (esta última exige `details`). La denunciante debe poder ver el objetivo (`ReportTargetResolver`, mismas reglas que al verlo: post con `PostQueryRepository.one`, mensaje solo participante y desde que entró, evento `EventService.findVisible`, comunidad pública/miembro no baneada); lo que no ve es el mismo 404 que lo inexistente. Denunciarse a una misma: 400. Única abierta por (denunciante, objetivo) con índice único parcial: la segunda es 409. Límite `asaltamontes.rate-limit.reports-per-day` (20), cuenta cada intento. Se guarda copia del texto (MESSAGE, POST, COMMENT) y `reported_user_id` (autora) para el panel. La respuesta no dice nada de la denunciada. Exige correo verificado si `verification.enforce`. Sin listado para la denunciante (no pedido). **T23b2 (front):** opción «Denunciar» en menús de usuaria, mensaje, quedada, comunidad, publicación y comentario con selector de motivo; pendiente.

### T23c — Panel
- Roles: añadir `MODERATOR` global (migración que amplía el enum/columna), `@PreAuthorize`; nada de autoasignarse roles.
- `GET /api/admin/reports` (filtro por estado y tipo, cursor), detalle con el contenido, acciones: descartar, borrar contenido, suspender (temporal con fecha / permanente). Suspendida: `UserAccount.suspendedUntil`; el login falla con mensaje neutro y se revocan sus `RefreshSession` (T06). Registro de auditoría de cada acción.
- Front: `pages/admin` (ya existe `Admin.jsx`) con la cola y el detalle, solo visible a ADMIN/MODERATOR.
- Aquí se decide el destino de los datos de cuentas borradas (BUG-065, BUG-068) con T24.

**Implementación (T23c1, V24):** `MODERATOR` entra en el enum existente `UserAccountRole` y en el CHECK de `users.role` (localizado por definición, BUG-049); la autoridad `ROLE_MODERATOR` sale del `getAuthorities()` que ya había, sin tocar `JwtUtil`, `JwtAuthFilter` ni `AuthService`; el rol se lee de la BD en cada petición, así que quitarlo surte efecto al instante. No hay endpoint para cambiar roles (se asignan en la BD). `/api/admin/reports` (`@PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")` + comprobación en el servicio): lista con filtros `status`/`type` y cursor opaco (abiertas, las más antiguas primero), detalle (copia del texto, quién denuncia, a quién, otras denuncias abiertas del objetivo, si el contenido sigue existiendo), `dismiss` (solo esa denuncia) y `remove-content` (post/comentario: borrado lógico; mensaje: borrado; quedada: cancelada; comunidad: solo ADMIN; cierra todas las denuncias abiertas del objetivo; USER → 400). Cada acción escribe `admin_audit_log` en la misma transacción. Las del `security-reviewer` en T23b: copia también de usuaria/evento/comunidad (título + descripción, nombre + bio); cooldown de 7 días tras descartar una denuncia (mismo denunciante y objetivo → 409); `details` y la copia se sirven como texto plano (el front no debe interpretarlos como HTML); purga de `details` y copia a los 30 días de resolver (`asaltamontes.reports.retention-days`, tarea diaria 04:15).

**Implementación (T23c2, V25):** `users.suspended_until` y `suspended_permanently`; `UserAccount.isSuspended()` (la temporal termina sola) y `isAccountNonLocked()`. Extensión en el sitio de la autenticación, sin clases nuevas: `AuthenticationProviderImpl` (contraseña correcta pero suspendida → el mismo 401 neutro), `AuthService` (Google login, refresh y `verifyAccessToken`), `JwtAuthFilter` (el access token vigente deja de valer al instante) y `WebSocketJwtInterceptor` (conexiones nuevas; una conexión STOMP ya abierta sigue hasta que se cierre). Al suspender se revocan todas sus `RefreshSession`. `POST /api/admin/reports/{id}/suspend-user` (`days` 1-3650 o `permanent`) y `POST /api/admin/users/{username}/unsuspend`. Nadie suspende a una ADMIN; solo una ADMIN suspende a una MODERATOR; nadie resuelve una denuncia sobre sí misma. Auditado (`USER_SUSPENDED`, `USER_UNSUSPENDED`).

### T23d — Imágenes privadas
- Decisión técnica a tomar al implementar, con medición en la Pi: `auth_request` de nginx contra un endpoint interno del backend (un salto por imagen, cacheable unos segundos) frente a URLs firmadas HMAC con caducidad (sin salto, pero cambia los DTO y la caché del navegador). Recomendación inicial: URLs firmadas solo para imágenes de contenido privado (comunidades privadas, perfiles privados, fotos de publicaciones privadas); avatares públicos siguen igual.
- La retirada de una imagen denunciada debe surtir efecto: sin `immutable` en su caché.

## Criterios de aceptación
- [ ] Un perfil privado no filtra seguidores, seguidos, quedadas, comunidades ni publicaciones a quien no está aprobada; los tests lo prueban por cada endpoint.
- [ ] Toda denuncia llega a la cola y una admin puede resolverla; las acciones quedan auditadas.
- [ ] Una cuenta suspendida no puede iniciar sesión ni refrescar el token.
- [ ] Ninguna URL de imagen privada sirve a quien no tiene permiso (tras T23d).

## Tests requeridos
- Integración por cada endpoint afectado por la privacidad; solicitudes (idempotencia, cancelación, cambio de privacidad); rate limit de denuncias; permisos del panel (USER recibe 403); suspensión y revocación de sesiones.
- Pasar `security-reviewer` en cada PR (todas tocan permisos o datos personales).

## Decisiones de Yago (2026-10-29, D-025)
- Perfil privado visto por quien no la sigue: nombre, avatar, bio, recuentos y botón «Seguir» (que envía una solicitud). **Ni publicaciones, ni quedadas que organiza, ni comunidades.** Por tanto la lista de la sección T23a «Sí se ven…» queda así y no se muestran quedadas ni comunidades en el perfil ajeno hasta aprobar.
- Suspensión de cuentas: número libre de días, con atajos de 1, 7 y 30 (más «permanente»).
