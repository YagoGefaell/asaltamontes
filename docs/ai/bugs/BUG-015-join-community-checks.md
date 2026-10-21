# BUG-015 — Unirse a una comunidad no valida existencia, duplicado ni baneo

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/community/service/CommunityService.java` → `joinCommunity()`

## Qué pasa
`getReferenceById` no comprueba que la comunidad exista (la FK falla → 409 genérico); el duplicado se detecta solo por la violación de unique; un miembro `BANNED` no puede volver a unirse solo porque ya tiene fila, lo que funciona por accidente y no por diseño.

## Cómo reproducir
`POST /api/communities/99999/join` → 409 "Data integrity violation".

## Arreglo propuesto
Validaciones explícitas con 404/409/403 (T14).
