# T22 — Muro de comunidad y Home con feed real

- **Estado:** pendiente (T22a1 en PR; spec refinada; se divide en T22a backend, T22b front de comunidad y perfil, T22c Inicio y Explorar)
- **Fase:** 2b — Comunidades
- **Depende de:** T14, T17
- **Rama sugerida:** `feat/T22-community-feed`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** refinada (2026-10-29). Contexto: V16 es la última migración; las imágenes se referencian por `media_id` (V8, `MediaReferenceService`, limpieza de huérfanos en `MediaOrphanCleanupJob`); paginación con `OpaqueCursor` (T30c); ids públicos UUID v7 (D-021).

## Objetivo
Inicio sin feed real todavía. Sustituirlo por contenido real: publicaciones de comunidades y de las usuarias que sigues, con sugerencias intercaladas.

## Decisiones de Yago (2026-10-28, revisión de la PR #109)
- **Inicio es de publicaciones**: el feed con, intercaladas entre publicaciones, filas deslizables (con su título) de usuarias para seguir y de comunidades recomendadas. **Sin quedadas** en Inicio: viven en su pestaña.
- **Publicación = texto con foto opcional** (estilo Strava/Facebook, no solo foto).
- **Quién publica**: a futuro cualquier usuaria, en su perfil (lo ven sus seguidoras) y en sus comunidades (lo ven sus miembros). Si para la demo no da tiempo, como mínimo en comunidades.
- **Crear publicación es una pantalla aparte**, como en Instagram (un acceso propio de «crear», no un editor dentro de Inicio). Dónde va el acceso (pestaña de la barra inferior o `+` en la cabecera) se decide al refinar esta spec.
- **Buscar sin texto**: cuadrícula visual de publicaciones con foto, estilo «Explorar» de Instagram.
- Mientras tanto (T29h): Inicio muestra el estado vacío «Aún no hay publicaciones» y las filas de sugerencias; Buscar sin texto queda en blanco; al tocar el campo aparecen las pestañas y las búsquedas recientes, sin esperar a que escriba.

## Enfoque
- `posts` (author, community_id, text ≤ 2000, media ids hasta 4, created_at, like_count, comment_count, deleted_at), `post_likes`, `post_comments` (un solo nivel). Contadores desnormalizados con `UPDATE` atómicos, como los follows.
- Publicaciones en comunidades y en el perfil personal (ver decisiones arriba); si hay que recortar para la demo, primero comunidades.
- Home = "fan-out on read" con cursor: posts de mis comunidades + de las personas que sigo, ordenados por fecha, con filas de sugerencias intercaladas (sin quedadas). Con miles de usuarias basta con la consulta e índices `(community_id, created_at DESC)`; documentar cuándo convendría pasar a fan-out on write.
- Moderación: autora y moderadoras borran; denunciar llega con T23.
- Respeta los bloqueos (T13) y las comunidades privadas (T19).

## Refinado (2026-10-29)

### División
| PR | Contenido | Depende de |
|---|---|---|
| **T22a** | Backend: migración, publicaciones, likes, comentarios, feed de Inicio, cuadrícula de Explorar | — |
| **T22b** | Front: crear publicación (pantalla aparte), publicación en comunidad y en perfil, likes y comentarios | T22a |
| **T22c** | Front: Inicio con feed real e intercalado de filas de sugerencias; Buscar sin texto con cuadrícula | T22a, T22b |

### Backend (T22a)
- Migración nueva (V17 o la siguiente libre): `posts(id bigint, public_id uuid, author_id, community_id null, body text ≤ 2000, like_count, comment_count, created_at, deleted_at)`, `post_media(post_id, media_id, position)` (máx. 4), `post_likes(post_id, user_id, PK compuesta)`, `post_comments(id, public_id, post_id, author_id, body ≤ 1000, created_at, deleted_at)`.
  Índices: `(community_id, created_at DESC, id DESC)`, `(author_id, created_at DESC, id DESC)`, parcial `WHERE deleted_at IS NULL`. `media_id` con FK `ON DELETE SET NULL` y las referencias registradas en `MediaReferenceService` para que la limpieza de huérfanos no borre fotos en uso.
- `community_id` nulo = publicación en el perfil personal; la ven quienes siguen a la autora (y ella). Con `community_id`, la ven los miembros (no baneadas); en comunidades públicas el texto solo lo ven miembros (decisión conservadora hasta que Yago diga otra cosa).
- Endpoints (todo paginado con cursor opaco, ids públicos):
  - `POST /api/posts` (`communitySlug` opcional, `body`, `mediaIds`), `DELETE /api/posts/{publicId}` (autora, o propietaria/moderadora de la comunidad; borrado lógico).
  - `GET /api/communities/{slug}/posts`, `GET /api/users/{username}/posts`, `GET /api/posts/{publicId}`.
  - `PUT|DELETE /api/posts/{publicId}/like` (idempotentes; `UPDATE like_count` atómico, sin tocar si no cambió nada: lección de BUG-007/070).
  - `GET|POST /api/posts/{publicId}/comments`, `DELETE /api/posts/{publicId}/comments/{publicId}` (autora del comentario o moderación).
  - `GET /api/feed` (Inicio): posts de mis comunidades y de quienes sigo, por fecha. Una consulta con `UNION`/`OR` sobre los índices anteriores, sin N+1 (fotos y autora por proyección). Documentar en la PR el umbral para pasar a fan-out on write.
  - `GET /api/explore/posts`: publicaciones **con foto** de comunidades públicas y perfiles públicos, para la cuadrícula de Buscar. Orden por fecha (el ranking queda fuera).
- DTO: `PostDto` con `likedByMe`, contadores, `author` (`username`, nombre, avatar), `community {slug, name}`, `media[] {thumb, medium, large}`, `canDelete`. Ninguna PK numérica.
- Reglas: bloqueos en cualquier dirección (D-022) ocultan publicaciones, comentarios y likes de esa usuaria (404 uniforme al abrir); comunidades privadas (T19) solo a miembros; baneadas fuera; límites por usuaria configurables con 429 + `Retry-After` (p. ej. 20 posts/día, 60 comentarios/h, 200 likes/h); texto sin HTML (se renderiza como texto en el front).
- Notificaciones (T18): `POST_COMMENT` a la autora (con cooldown, no a ti misma ni si bloqueada). Likes sin notificación en v1.
- Borrar cuenta (T23/BUG-065): las publicaciones de una cuenta borrada se eliminan; dejarlo anotado allí.

### T22a partida en dos PRs (2026-10-29)
La spec de T22a es demasiado grande para una PR revisable en 30 min, así que se parte:

| PR | Contenido |
|---|---|
| **T22a1** | Migración V18 **completa** (`posts`, `post_media`, `post_likes`, `post_comments`: el esquema se revisa una sola vez), `POST /api/posts`, `GET|DELETE /api/posts/{id}`, `GET /api/communities/{slug}/posts`, `GET /api/users/{username}/posts`, `GET /api/feed`, límite de publicaciones, fotos (`POST_IMAGE`) y su liberación al borrar. `PostDto` ya trae `likeCount`, `commentCount`, `likedByMe` (0/0/false hasta T22a2). |
| **T22a2** | `PUT|DELETE /api/posts/{id}/like` (idempotentes, `UPDATE` atómico), `GET|POST|DELETE` de comentarios, límites de comentarios y likes, notificación `POST_COMMENT`, `GET /api/explore/posts`. |

Decisiones tomadas en T22a1 (sin respuesta de Yago todavía; cambiarlas es barato):
- **Texto solo para miembros**, también en comunidades públicas (la decisión conservadora de arriba). Hasta que Yago diga otra cosa, una comunidad pública no enseña publicaciones a quien no es miembro.
- **Silenciadas (`MUTED`)** no publican en la comunidad (403), igual que en su chat. Publicar en su perfil sigue siendo suyo. Baneadas y ajenas: 404 como una comunidad inexistente.
- **Perfil ajeno**: `GET /api/users/{username}/posts` lista solo las publicaciones *del perfil* (no las de comunidades) y devuelve página vacía a quien no la sigue; 404 si hay bloqueo. `is_public_profile` no se usa todavía (no hay perfiles privados implementados en seguidoras); entrará con Explorar.
- **Borrar**: la autora, o propietaria/moderadora en publicaciones de comunidad. Borrado lógico, `post_media` se elimina y las fotos que nada más referencia se borran (los ficheros, tras el commit).
- **Orden y cursor**: `(created_at DESC, public_id DESC)` (UUID v7), porque el cursor opaco solo lleva el id público.
- **Cuenta borrada**: las FK `ON DELETE CASCADE` eliminan publicaciones, fotos enlazadas, likes y comentarios; las publicaciones no añaden trabajo a BUG-065 (que sigue abierto: `deleteUser` falla si la cuenta tiene seguidoras/seguidos).
- **Feed**: fan-out on read; el umbral para pasar a fan-out on write está en el Javadoc de `PostQueryRepository`.
- Pregunta abierta para T22a2 (Explorar): la spec dice «comunidades públicas y perfiles públicos», pero el texto de comunidad es solo para miembros. Hay que decidir si la cuadrícula muestra fotos de comunidades públicas a no miembros (se filtraría el contenido que aquí es privado).

### Frontend (T22b y T22c)
- Crear publicación: pantalla aparte (`/posts/new`). Acceso: botón `+` en la cabecera de Inicio y, desde una comunidad, «Publicar» preseleccionando la comunidad. No se añade pestaña a la barra inferior (DESIGN: 5 pestañas).
- Selector de destino: «Mi perfil» o una de mis comunidades. Hasta 4 fotos con el flujo de `features/media`. Contador de caracteres.
- Tarjeta de publicación (`shared/ui` o `features/posts`): cabecera autora + comunidad, texto, fotos, like y comentarios; menú con «Eliminar» según `canDelete`.
- Pestaña «Publicaciones» en comunidad y en perfil (propio y ajeno).
- Inicio: lista infinita con cursor; cada 5 publicaciones una fila deslizable (usuarias o comunidades sugeridas, ya existentes); estado vacío actual si no hay nada.
- Buscar sin texto: cuadrícula de 3 columnas con miniaturas (`thumb`); al abrir, la tarjeta. Mantiene las pestañas al tocar el campo.
- Siguen aplicando `DESIGN.md` y `/ui-review` (390 y 360, claro/oscuro).

## Fuera de alcance (v1)
Ranking algorítmico, menciones, hashtags, compartir/repostear, edición de publicaciones, vídeos, denuncias (T23), notificación de likes, fijar publicaciones.

## Criterios de aceptación
- [ ] Una usuaria publica en su perfil y en una comunidad suya, con 0–4 fotos; no puede publicar en comunidades donde no es miembro ni en las que está baneada.
- [ ] El feed muestra solo lo que debe verse, paginado, sin duplicados entre páginas, y sin bloqueadas.
- [ ] Like y comentario son idempotentes y los contadores cuadran bajo concurrencia.
- [ ] Una foto en uso no la borra la limpieza de huérfanos; al borrar la publicación se libera.
- [ ] Ninguna respuesta incluye ids numéricos.

## Tests requeridos
- Integración: visibilidad (pública, privada, perfil, seguidoras, bloqueo, baneada), paginación con cursor, permisos de borrado, límites 429, likes concurrentes, número de consultas del feed constante (sin N+1).
- Front: tarjeta, creación (validaciones y subida), scroll infinito de Inicio, cuadrícula de Buscar.
