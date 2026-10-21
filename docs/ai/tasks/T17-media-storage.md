# T17 — Almacenamiento de imágenes (MinIO / S3)

- **Estado:** pendiente
- **Fase:** Transversal
- **Depende de:** T07
- **Rama sugerida:** `feat/T17-media-storage`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

> Raspberry Pi 5 (D-016): cualquier imagen nueva (MinIO, Prometheus, Grafana, Uptime Kuma…) debe existir para `linux/arm64`, y hay que vigilar la RAM; si no cabe, preferir servicios externos gratuitos.

## Objetivo
Hoy las imágenes son URLs pegadas a mano (`profilePictureUrl`, `imageUrl`, `groupImage`). Hace falta subir fotos de verdad: avatar, portada de comunidad, imagen de evento, imagen de grupo y, más adelante, posts (T22).

## Enfoque acordado (D-004)
- **MinIO** en el compose del servidor (API S3), con el SDK de AWS S3 v2 en el backend. Migrar luego a Cloudflare R2/S3 solo cambiando la configuración.
- **Subida directa desde el navegador** con URL prefirmada: `POST /api/media/uploads` `{purpose, contentType, size}` → `{uploadUrl, mediaId}`; el cliente hace `PUT` a MinIO; `POST /api/media/{id}/complete` → el backend valida (tipo real por magic bytes, ≤ 10 MB, dimensiones), **elimina EXIF/GPS** (seguridad: las fotos de móvil llevan la ubicación de casa), genera variantes (`thumb` 128, `medium` 640, `large` 1280, WebP) y marca la media como lista.
- Tabla `media` (owner, purpose, status, keys de las variantes, tamaño, creada…) y referencias por `media_id` en lugar de URLs libres (migración que conserva las URLs actuales como "externas").
- Servir las imágenes a través de nginx (`/media/…` → MinIO, bucket público solo de lectura para las variantes procesadas) con caché larga.
- Limpieza programada de subidas no completadas en 24 h y de medias huérfanas.

## Criterios de aceptación (borrador)
- Subir un avatar desde el móvil; se ve redimensionado; el archivo servido no contiene EXIF.
- Un archivo que no es imagen, o que pesa más de 10 MB, se rechaza.
- `AvatarUploader.jsx` usa el flujo nuevo; los campos de URL libre desaparecen de los formularios.
