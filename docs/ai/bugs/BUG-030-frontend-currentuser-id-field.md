# BUG-030 — El frontend usa `currentUser.id`, pero la API devuelve `userId`

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/chat
- **Tarea que lo resuelve:** T09
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/features/messages/components/conversations.provider.jsx` → `buildPreviewPrefix` (`currentUser?.id`); `UserSelfDto.userId`

## Qué pasa
La comparación por id siempre falla y depende del fallback por username. Combinado con BUG-019, la vista previa "Tú: …" falla tras un cambio de username.

## Cómo reproducir
—

## Arreglo propuesto
Usar `currentUser?.userId` (T09).
