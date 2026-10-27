# T17 — Almacenamiento de imágenes

- **Estado:** hecho (T17a, T17b, T17c1, T17c2 y T17c3)
- **Fase:** Transversal
- **Depende de:** T07 (hecho: nginx same-origin, `client_max_body_size 10m`), T05 (hecho)
- **Rama sugerida:** `feat/T17a-media-backend`, `feat/T17b-media-frontend`, `refactor/T17c-media-references`
- **Cierra bugs:** —
- **Tamaño:** L → 3 PRs
- **Spec:** refinada el 2026-10-21. Almacenamiento decidido: **opción A, sistema de archivos** (D-004 revisada).

## Objetivo
Hoy las imágenes son URLs pegadas a mano (`UserProfile.profilePictureUrl`, `Community.imageUrl`, `Conversation.groupImage`). Hace falta subir fotos de verdad desde el móvil (avatar, portada de comunidad, imagen de grupo y luego de eventos y posts, T15/T22), **sin EXIF/GPS** (las fotos del móvil llevan la ubicación de casa) y redimensionadas.

## Decisión: dónde guardar los ficheros (D-004) — **elegida la opción A** (Yago, 2026-10-21)
D-004 eligió **MinIO**, pero MinIO dejó de publicar imágenes Docker de la edición comunitaria en octubre de 2025 (la última queda congelada y sin parches de seguridad) y su repositorio está archivado desde abril de 2026. Opciones:

| Opción | En la Pi (arm64, 4 GB) | Paso a hosting | Complejidad |
|---|---|---|---|
| **A. Sistema de archivos** (volumen `media_data` compartido backend → nginx) detrás de una interfaz `MediaStorage` | Sin servicio extra; nginx sirve `/media/` directamente | Se añade la implementación S3/R2 de la misma interfaz | La más baja |
| B. **Garage** (S3 compatible, binario Rust ligero, arm64) | Un servicio más (~50-100 MB) | Igual que S3 (solo configuración) | Media: claves, bucket, layout del clúster |
| C. **SeaweedFS** o **RustFS** | Un servicio más, más pesado (SeaweedFS) o muy nuevo (RustFS) | Igual que S3 | Media/alta |
| D. Cloudflare R2 ya | Nada en la Pi | Ya está | Depende de una cuenta externa; requiere salida a internet |

**Recomendación: A** para la demo. Es lo más ligero y estable en la Pi, la interfaz `MediaStorage` deja el paso a S3/R2 como un cambio de configuración + una clase, y las subidas pasan por el backend (que de todos modos tiene que recodificar la imagen para quitar el EXIF). Las URLs prefirmadas solo aportan algo con un almacenamiento S3: se añadirían con la implementación S3. D-004 ya está actualizada: binarios en un volumen, nunca en Postgres.

## Contexto (código actual)
- Backend: `UserProfile.profilePictureUrl`, `Community.imageUrl`, `Conversation.groupImage` (texto libre). `UserValidator.validateProfileChange` solo comprueba que empiecen por `http(s)://`. Los DTO de búsqueda y detalle devuelven esas URLs tal cual.
- Frontend: los campos de URL libre están en `pages/settings/EditProfile.jsx`, `pages/communities/components/CommunityCreatorPanel.jsx` y `pages/messages/components/GroupCreatorPanel.jsx`. Las imágenes se pintan en `shared/ui/UserCard.jsx`, `Profile.jsx`, `Communities.jsx`, `CommunityProfileDetail.jsx`, `Messages.jsx`, `Search.jsx` y `FollowListPage.jsx`. `shared/ui/AvatarUploader.jsx` existe pero no se usa en ninguna pantalla, y solo es un campo de texto para pegar una URL con vista previa.
- nginx (T07a): `client_max_body_size 10m`; `/assets/` con caché inmutable.

## Alcance

### T17a — Backend: subida, procesado y almacenamiento
- Migración `V<n>__media.sql`: tabla `media` (`id` UUID PK, `owner_id` FK users, `purpose` (`AVATAR`, `COMMUNITY_COVER`, `GROUP_IMAGE`, `EVENT_IMAGE`), `status` (`READY`), `width`, `height`, `bytes`, `created_at`) + índice `(owner_id, created_at DESC)`.
- `media/` (nuevo dominio): `MediaController` `POST /api/media` (multipart, campo `file` + `purpose`) → `201 { id, urls: { thumb, medium, large } }`.
- Validación: tamaño ≤ 10 MB (`spring.servlet.multipart.max-file-size`); **tipo real por magic bytes** (JPEG, PNG, WebP de entrada), nunca por la extensión ni el `Content-Type`; dimensiones ≥ 64 px y ≤ 8000 px (contra bombas de descompresión, comprobando la cabecera antes de decodificar).
- Procesado: **Thumbnailator** (Java puro, funciona en arm64) + **TwelveMonkeys ImageIO** para leer WebP y JPEG CMYK. Aplica la orientación EXIF y re-codifica a **JPEG** (calidad 0.85) en 3 variantes (`thumb` 128, `medium` 640, `large` 1280, sin ampliar las pequeñas). La re-codificación elimina **todo** el EXIF/GPS. WebP de salida queda fuera: en Java puro no hay un codificador fiable en arm64.
- `MediaStorage` (interfaz `put/delete/publicUrl`) + `FileSystemMediaStorage` (opción A) en `MEDIA_DIR` (volumen `media_data`), con rutas `/{purpose}/{id}/{variant}.jpg`. La URL pública es `/media/...`.
- Límite por usuaria: 30 subidas/hora (reutiliza el `RateLimiter` de T06b).
- Compose: volumen `media_data` montado en el backend (rw) y en el frontend/nginx (ro); nginx `location /media/ { alias …; expires 1y; add_header Cache-Control "public, immutable"; }`. El backup de T07b **no** cubre `media_data`: anotarlo en la PR (o añadir un `tar` diario al servicio `backup`).
- Variables nuevas: `MEDIA_DIR`, documentadas.
- Tests: subida de un JPEG con EXIF GPS → las variantes no contienen EXIF (comprobado con `metadata-extractor` o leyendo los marcadores APP1); un PNG renombrado a `.jpg` se procesa por su contenido; un PDF con `Content-Type: image/jpeg` → 400; > 10 MB → 413/400; foto vertical con orientación EXIF 6 → variante en vertical; dimensiones de cada variante.

**Notas de implementación de T17a** (decisiones menores al implementar; la migración es `V7__media.sql`):
- Límite adicional de **24 Mpx** decodificados (`asaltamontes.media.max-pixels`) además de 64-8000 px por lado: un PNG ocupa 4 B/px y la Pi tiene `-Xmx512m` (surefire corre con ese heap y un test procesa un PNG de 24 Mpx).
- Semáforo de 1 permiso (`max-concurrent-processing`) que se toma **antes** de leer los bytes de la subida, con espera de 3 s; si no hay hueco, 429 con `Retry-After`.
- Respuestas: archivo > 10 MB → 413; no es imagen / corrupta / muy pequeña / muy grande en píxeles → 400 con mensaje en español; > 30 subidas/hora → 429 con `Retry-After`.
- Rutas: `/{avatar|community-cover|group-image|event-image}/{uuid}/{thumb|medium|large}.jpg`. El UUID es aleatorio (`SecureRandom`): la URL es la capacidad de verla, como en los CDN de las redes sociales. Cualquier usuaria autenticada puede subir; el control de "esta media es tuya y del `purpose` correcto" llega con T17c, cuando haya referencias.
- Las medias huérfanas (subidas y nunca referenciadas) se limpian en T17c. **Requisito de T17c** (revisión de seguridad de #64): además de la limpieza, una cuota de almacenamiento por usuaria (bytes en `media` por `owner_id`) para que el límite de 30/h no permita llenar el disco de la Pi con el tiempo.
- **Requisito de T17b** (revisión de seguridad de #64): cualquier endpoint o campo que referencie una media debe validar que `media.owner_id` es la usuaria actual y que el `purpose` es el correcto, y que la usuaria tiene permiso sobre el recurso destino (comunidad, grupo, evento).
- El backup de T07b **no** cubre `media_data` (documentado en el README, con un `tar` de ejemplo).

### T17b — Frontend: componente de subida

**Cómo quedó T17b** (PR `feat/T17b-media-frontend`; se hizo backend de enlace + frontend en una sola PR, sin migración):
- Sin columnas `*_media_id` (llegan en T17c), una URL guardada en `profilePictureUrl` / `Community.imageUrl` / `Conversation.groupImage` que apunta a nuestro almacén (`/media/<purpose>/<uuid>/...`, también con host delante) se trata como **referencia a una media**: `MediaReferenceService.checkLinkable` exige que `media.owner_id` sea la usuaria que enlaza (si no existe o es de otra → **404**, la misma respuesta para no poder sondear ids) y que el `purpose` coincida (si no → **400**). Las URLs externas no se tocan (T17c las retira).
- "Puede modificar el recurso": hoy el único recurso editable con imagen es el perfil propio (`PATCH /api/users/me`), y comunidad y grupo solo aceptan imagen al **crearse** (la creadora es OWNER / administradora); no existen endpoints de edición de comunidad ni de grupo, ni el evento tiene imagen en la API (`Event.imageUrl` no se escribe). **Requisito de T17c y de los endpoints de edición futuros**: al añadirlos, comprobar también OWNER/MODERATOR (comunidad), administradora (grupo) y organizadora (evento) antes de llamar a `checkLinkable`.
- Revisión independiente de #68: la comprobación es estricta (regex anclada con `$`, sensible a mayúsculas; se rechazan `..`, `%`, `\`, espacios/control; los valores relativos deben ser exactamente una URL interna; una URL absoluta que contenga `/media/` también). **BUG-057** (URLs externas libres en `imageUrl`/`groupImage` y sin `@Size`) sigue abierto y no se arregla aquí; T17c retira las URLs libres.
- Frontend: `shared/ui/ImageUploader.jsx` (genérico) y `AvatarUploader` como envoltorio; servicio `features/media/services/media.service.js` (XHR con progreso, renueva el token una vez si da 401). Sin compresión previa en el cliente (el backend ya redimensiona; 10 MB es suficiente para fotos de móvil).
- Convertir `shared/ui/AvatarUploader.jsx` en un `ImageUploader` genérico (manteniendo `AvatarUploader` como envoltorio para el avatar): `<input type="file" accept="image/*">` (en iOS abre cámara o galería y convierte HEIC a JPEG), vista previa local (`URL.createObjectURL`), validación previa de tamaño (≤ 10 MB) y tipo, barra de progreso (subida con `XMLHttpRequest`: `fetch` no da progreso), errores del backend y reintentar.
- Usarlo en `EditProfile.jsx` (avatar), `CommunityCreatorPanel.jsx` (portada) y `GroupCreatorPanel.jsx` (imagen de grupo). En esta PR se sigue guardando la URL devuelta (`urls.medium`) en el campo actual, para no depender de T17c.
- Tests (Vitest): rechaza > 10 MB y no-imágenes sin llamar a la API; muestra el progreso y el error del backend.

### T17c — Referencias por `media_id`
- Migración: `user_profiles.avatar_media_id`, `communities.cover_media_id`, `conversations.group_media_id` (FK a `media`, `ON DELETE SET NULL`). Las URLs actuales se conservan como "externas" (las columnas de texto siguen funcionando para los datos antiguos).
- Los DTO devuelven `avatar: { thumb, medium, large }` (o la URL externa como `medium`). Las peticiones de edición aceptan `avatarMediaId` y comprueban que la media es de la usuaria y del `purpose` correcto.
- Quitar los campos de URL libre de los formularios.
- Limpieza programada (patrón de `maintenance/`): medias con más de 24 h sin referenciar → borrar ficheros y filas. Al cambiar el avatar, borrar el anterior.

**División de T17c** (2026-10-23): T17c1 (backend: referencias, limpieza y cuota) y T17c2 (backup) van en la misma PR `refactor/T17c-media-references`; T17c3 (frontend) queda pendiente y lo hace otro agente.

**Cómo quedaron T17c1 y T17c2:**
- Migración `V8__media_references.sql`: `user_profiles.avatar_media_id`, `communities.cover_media_id`, `conversations.group_media_id` (uuid, FK a `media` `ON DELETE SET NULL`, índice parcial). Convierte en referencia las URLs internas existentes cuando la media existe, es de la dueña del recurso y tiene el `purpose` correcto; el resto queda como URL externa heredada. Evento: **no** (`Event.imageUrl` no se escribe desde la API); cuando se pueda subir imagen de evento, se añade `event_image_media_id` y se incluye en `MediaRepository.isReferenced`/`findUnreferencedCreatedBefore`.
- **Decisión de diseño**: las columnas de texto (`profile_picture_url`, `image_url`, `group_image`) se conservan y, para una referencia propia, guardan la URL de la variante `medium` (se rellena al enlazar). Así las ~15 consultas JPQL de listados y los DTO no cambian, no hay joins nuevos y el frontend actual sigue funcionando. La fuente de verdad de "a quién pertenece la imagen" y de la limpieza es el `*_media_id`. Las peticiones aceptan `avatarMediaId` / `coverMediaId` / `groupMediaId` y, por compatibilidad con el T17b del frontend, la URL interna (se normaliza a la variante `medium` reconstruida desde el id).
- `MediaReferenceService.link(...)` sustituye a `checkLinkable`: mismo control (404 si no existe o es de otra, 400 si el `purpose` no coincide, rutas con `..`/`%`/`\` rechazadas) y toma un bloqueo compartido sobre la fila de `media` dentro de la transacción que guarda la referencia. **BUG-057 resuelto** (ver el bug): URLs externas nuevas rechazadas, `@Size(255)` y nombre de grupo `@Size(80)`.
- Al cambiar o quitar el avatar (`profilePictureUrl: ""` lo quita) se borra la imagen anterior si nadie más la referencia.
- Limpieza (`media/maintenance/MediaOrphanCleanupJob`, `@Scheduled`, `MEDIA_CLEANUP_CRON`, por defecto 04:15): borra de disco y BD las medias sin referencia con más de `MEDIA_ORPHAN_GRACE_PERIOD` (24 h). **Carrera subida/enlace**: el plazo evita casi todo, y el caso restante (enlazar una media justo cuando cumple el plazo) se cierra con bloqueos de fila: enlazar toma `FOR SHARE`; la limpieza toma `FOR UPDATE`, y solo después comprueba las referencias con una consulta nueva. O gana el enlace (la media queda referenciada) o gana la limpieza (el enlace da 404). Los ficheros se borran tras el commit.
- Cuota (`MEDIA_MAX_BYTES_PER_USER`, 50 MB, suma de `media.bytes` de la usuaria): `POST /api/media` responde **409** con "Has alcanzado tu límite de almacenamiento de imágenes (50 MB)...". Se comprueba antes de procesar y de nuevo con el tamaño real; es un límite blando (subidas simultáneas de la misma usuaria pueden pasarse un poco, acotadas por el límite de 30/h y el semáforo).
- Backup (T17c2): `scripts/backup/backup-media.sh`, llamado por `backup-loop.sh` tras cada volcado; `media_data` montado `ro` en el servicio `backup`. Conserva solo `BACKUP_MEDIA_KEEP` (3) copias en `/backups/media`; comprueba que hay sitio (tamaño de la carpeta + `BACKUP_MIN_FREE_MB`) y, si no, no copia, lo deja en el log y en `/backups/media/status` (NO afecta al healthcheck del servicio, para no romper `up --wait` del deploy; se vigila con el fichero `status`, ver README). Restauración en el README.
- **Revisión independiente de #70**: V8 reescribe también la columna de texto (quita un host ajeno delante de nuestra ruta) y solo enlaza la imagen de un grupo si su dueña es participante del grupo (las conversaciones no tienen creadora); reenviar sin tocar el valor externo ya guardado se acepta antes de los filtros de caracteres; la cuota relee el total tras procesar; `backup-media.sh` borra `*.partial` al empezar; `MediaReferenceColumnsIT` falla si una columna `*_media_id` del esquema no está en las consultas de referencias.
- **Requisito antes de producción**: no hay tope global de disco para `media_data` (solo cuota por usuaria): con muchas cuentas, 50 MB x usuarias puede llenar el disco. Definir un tope global o alertas de espacio (T23) antes de abrir el registro.
- **Pendiente (T17c3, frontend)**: enviar `...MediaId` en vez de la URL, quitar de los formularios cualquier campo de URL libre que quede, y (opcional) cambiar los DTO de lectura a `avatar: { thumb, medium, large }` (hoy devuelven la URL `medium` en los campos de siempre). También el perfil ICC opcional.
- **Cómo quedó T17c3 (frontend)**: `ImageUploader.onChange(url, mediaId)` (o `("", null)` al quitar). Perfil envía `avatarMediaId` (y `profilePictureUrl: ""` solo para quitar la foto), comunidad `coverMediaId`, grupo `groupMediaId`; ningún formulario envía ya una URL de imagen, y no quedaba ningún campo de URL libre (todos usaban el uploader). Evento: la API no admite imagen aún, nada que cambiar.
- **Pendiente tras T17c3 (Yago, 2026-10-27):** (1) DTO de lectura `avatar:{thumb,medium,large}`; (2) perfil ICC opcional.
- **Fuera de T17c3 (decidido)**: los DTO de lectura siguen devolviendo la URL `medium` en `profilePictureUrl`/`imageUrl`/`groupImage`; cambiarlos a `avatar:{thumb,medium,large}` toca ~15 consultas JPQL y todos los consumidores del front (contrato grande), y no aporta hasta que se usen `thumb`/`large`. Se hará si se necesitan variantes por tamaño. Perfil ICC: sigue pendiente (opcional). El backend sigue aceptando la URL interna por compatibilidad; puede retirarse cuando no haya clientes antiguos.

## Fuera de alcance
- Imágenes de posts (T22), moderación de imágenes, CDN (después de la demo).
- Implementación S3/R2 de `MediaStorage` (cuando haya hosting).

## Criterios de aceptación
- [ ] Subir un avatar desde el móvil; se ve redimensionado en la app.
- [ ] El archivo servido no contiene EXIF (en particular, nada de GPS).
- [ ] Un archivo que no es imagen, o que pesa más de 10 MB, se rechaza con un mensaje claro.
- [ ] Las imágenes se sirven desde nginx con caché larga; el backend no participa al servirlas.
- [x] (T17c) Los formularios ya no tienen campos de URL libre.

## Notas y riesgos
- La RAM de la Pi: decodificar una foto de 48 Mpx ocupa ~200 MB de heap. El límite de dimensiones y procesar **una imagen a la vez** (semáforo de 1-2 permisos) evitan quedarse sin memoria con `-Xmx512m` (T07a).
- Sin cgroup de memoria (T07a) no hay red de seguridad: el semáforo es imprescindible.

## Requisitos escritos antes de producción (respuestas de Yago, 2026-10-23)
Hallazgos del `security-reviewer` sobre #64 y decisiones posteriores. No bloquean la demo en la LAN (D-013), sí la producción abierta.

### T17b (frontend de medios + enlazar imágenes)
- Al enlazar una imagen a un recurso (perfil, comunidad, grupo, evento) el backend valida **que `media.owner_id` es la usuaria que enlaza, que el `purpose` coincide con el recurso y que la usuaria puede modificar ese recurso** (OWNER/MODERATOR de la comunidad, organizadora del evento, administradora del grupo). Test de IDOR: usar la imagen de otra usuaria → 403/404.

### T17c (referencias, limpieza y backups)
- **Limpieza de huérfanos**: tarea programada que borra de disco y BD las imágenes sin referencia pasado un plazo (p. ej. 24 h) y **cuota de almacenamiento por usuaria**.
- **Backup de `media_data`**: `tar` diario en el servicio `backup`, **conservando solo las 3 últimas copias** y **comprobando antes que hay espacio en disco** (si no lo hay, no copia y avisa en el log/health). Restauración documentada en el README.
- (Opcional) conservar el perfil ICC en el recodificado; hoy se pierde y las fotos Display P3 se ven apagadas.

### Antes de producción (ver también T23)
- Hoy `/media/` se sirve **sin autenticación** y la protección es un UUID aleatorio. Para imágenes de contenido privado (comunidades privadas, grupos, eventos de comunidad) hace falta otra estrategia (URLs firmadas con caducidad o `auth_request` de nginx). Decidirlo antes de abrir la plataforma; `Cache-Control: immutable` impide además retirar una imagen ya descargada.
