# T22 — Muro de comunidad y Home con feed real

- **Estado:** pendiente
- **Fase:** 2b — Comunidades
- **Depende de:** T14, T17
- **Rama sugerida:** `feat/T22-community-feed`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

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
