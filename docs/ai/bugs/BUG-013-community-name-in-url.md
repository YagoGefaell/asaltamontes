# BUG-013 — El nombre de la comunidad se usa como identificador de URL sin codificar

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/community + backend
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/features/communities/services/communities.service.js` (`/api/communities/${communityName}`), `router.jsx` (`communities/:communityName`)

## Qué pasa
Nombres con `/`, `#`, `?`, `%` o espacios generan rutas rotas (404 o una comunidad equivocada). Renombrar una comunidad rompería todos los enlaces compartidos.

## Cómo reproducir
Crear una comunidad "Trail Vigo/Sur" e intentar abrirla.

## Arreglo propuesto
Slug único inmutable (T14).

**Progreso (T14a):** el backend ya identifica las comunidades por `slug` (y acepta el nombre como respaldo). Falta que el frontend use el slug en rutas y enlaces: T14b.
