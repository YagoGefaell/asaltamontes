# BUG-038 — En las listas de seguidores y seguidos solo aparece el botón para las personas que ya sigues

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/pages/profile/FollowListPage.jsx` (`showFollowButton={user.isFollowing ? true : false}`)

## Qué pasa
El backend devuelve `isFollowing = null` para la propia usuaria y `true/false` para el resto. La condición oculta el botón "Seguir" a quien todavía no sigues, que es precisamente cuando más útil resulta.

## Cómo reproducir
Abrir la lista de seguidores de otra persona → no hay botón "Seguir" para quien no sigues.

## Arreglo propuesto
`showFollowButton={user.isFollowing !== null}`. Yago tenía este arreglo en local y lo descartó (2026-10-21): lo resuelve Claude en una PR `fix/BUG-038-*` propia.
