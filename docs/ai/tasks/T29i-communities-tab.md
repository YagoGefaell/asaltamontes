# T29i — Pestaña Comunidades: «tus comunidades»

- **Estado:** pendiente (propuesta de Yago, 2026-10-28)
- **Depende de:** T29h, T31c (backend)

## Reparto
Buscar → descubrir comunidades. Inicio → qué pasa (quedadas, más adelante publicaciones, T22). Comunidades → **tus** comunidades.

## Pantalla
- **Pendiente** (solo si hay algo): solicitudes por aprobar (moderadoras), invitaciones recibidas y solicitudes propias en espera.
- **Tus comunidades**, por actividad: último mensaje del chat con no leídos y próxima quedada.
- Enlace «Descubrir comunidades cerca de ti» → `/search`.
- Sin comunidades: estado vacío con 3–5 sugerencias cercanas (sin GPS: ciudad del perfil) y «Crear comunidad». El `+` de crear sigue en la cabecera.
- Se quita «Tus comunidades» de Inicio y la hoja de filtros ciudad/provincia de esta pantalla.
- Descartado: feed de actividad y descubrir por categorías.

## Backend (T31c)
`/api/communities/me` con último mensaje, no leídos y próxima quedada; endpoint que agrupe lo pendiente (solicitudes, invitaciones).
