# T17 — Almacenamiento de imágenes

- **Estado:** pendiente (T17a hecho en la PR de `feat/T17a-media-backend`; faltan T17b y T17c)
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
- Convertir `shared/ui/AvatarUploader.jsx` en un `ImageUploader` genérico (manteniendo `AvatarUploader` como envoltorio para el avatar): `<input type="file" accept="image/*">` (en iOS abre cámara o galería y convierte HEIC a JPEG), vista previa local (`URL.createObjectURL`), validación previa de tamaño (≤ 10 MB) y tipo, barra de progreso (subida con `XMLHttpRequest`: `fetch` no da progreso), errores del backend y reintentar.
- Usarlo en `EditProfile.jsx` (avatar), `CommunityCreatorPanel.jsx` (portada) y `GroupCreatorPanel.jsx` (imagen de grupo). En esta PR se sigue guardando la URL devuelta (`urls.medium`) en el campo actual, para no depender de T17c.
- Tests (Vitest): rechaza > 10 MB y no-imágenes sin llamar a la API; muestra el progreso y el error del backend.

### T17c — Referencias por `media_id`
- Migración: `user_profiles.avatar_media_id`, `communities.cover_media_id`, `conversations.group_media_id` (FK a `media`, `ON DELETE SET NULL`). Las URLs actuales se conservan como "externas" (las columnas de texto siguen funcionando para los datos antiguos).
- Los DTO devuelven `avatar: { thumb, medium, large }` (o la URL externa como `medium`). Las peticiones de edición aceptan `avatarMediaId` y comprueban que la media es de la usuaria y del `purpose` correcto.
- Quitar los campos de URL libre de los formularios.
- Limpieza programada (patrón de `maintenance/`): medias con más de 24 h sin referenciar → borrar ficheros y filas. Al cambiar el avatar, borrar el anterior.

## Fuera de alcance
- Imágenes de posts (T22), moderación de imágenes, CDN (después de la demo).
- Implementación S3/R2 de `MediaStorage` (cuando haya hosting).

## Criterios de aceptación
- [ ] Subir un avatar desde el móvil; se ve redimensionado en la app.
- [ ] El archivo servido no contiene EXIF (en particular, nada de GPS).
- [ ] Un archivo que no es imagen, o que pesa más de 10 MB, se rechaza con un mensaje claro.
- [ ] Las imágenes se sirven desde nginx con caché larga; el backend no participa al servirlas.
- [ ] (T17c) Los formularios ya no tienen campos de URL libre.

## Notas y riesgos
- La RAM de la Pi: decodificar una foto de 48 Mpx ocupa ~200 MB de heap. El límite de dimensiones y procesar **una imagen a la vez** (semáforo de 1-2 permisos) evitan quedarse sin memoria con `-Xmx512m` (T07a).
- Sin cgroup de memoria (T07a) no hay red de seguridad: el semáforo es imprescindible.
