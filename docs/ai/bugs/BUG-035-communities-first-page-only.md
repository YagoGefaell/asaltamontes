# BUG-035 — El listado de comunidades solo muestra las 20 primeras

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/features/communities/services/communities.service.js` → `getCommunitiesRequest` (devuelve `response.content`)

## Qué pasa
Se descarta la información de paginación y no hay forma de ver la comunidad nº 21 ni las siguientes.

## Cómo reproducir
Crear 25 comunidades.

## Arreglo propuesto
Scroll infinito o "cargar más" (T14).
