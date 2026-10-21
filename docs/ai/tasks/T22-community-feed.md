# T22 — Muro de comunidad y Home con feed real

- **Estado:** pendiente
- **Fase:** 2b — Comunidades
- **Depende de:** T14, T17
- **Rama sugerida:** `feat/T22-community-feed`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
`Home.jsx` muestra posts inventados. Sustituirlo por contenido real: publicaciones en las comunidades y próximas quedadas.

## Enfoque
- `posts` (author, community_id, text ≤ 2000, media ids hasta 4, created_at, like_count, comment_count, deleted_at), `post_likes`, `post_comments` (un solo nivel). Contadores desnormalizados con `UPDATE` atómicos, como los follows.
- De momento solo se publica en comunidades. Los posts de perfil personal, después de la demo.
- Home = "fan-out on read" con cursor: posts de mis comunidades + de las personas que sigo, ordenados por fecha, más un bloque con "Tus próximas quedadas" y "Quedadas cerca". Con miles de usuarias basta con la consulta e índices `(community_id, created_at DESC)`; documentar cuándo convendría pasar a fan-out on write.
- Moderación: autora y moderadoras borran; denunciar llega con T23.
- Respeta los bloqueos (T13) y las comunidades privadas (T19).
