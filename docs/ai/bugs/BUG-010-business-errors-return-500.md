# BUG-010 — Errores de negocio devuelven 500 y los errores inesperados no se registran

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/common
- **Tarea que lo resuelve:** T04
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/common/exceptions/GlobalExceptionHandler.java`; `CommunityService`, `CommunityController.leaveCommunity`, `MessageService` (usan `IllegalArgumentException`/`RuntimeException`)

## Qué pasa
"Community not found", "Owner cannot leave", "No tienes permiso para acceder a este chat", `AlreadyFollowingException`, `SelfFollowException`… acaban en el handler genérico → 500 "An unexpected error occurred". El handler genérico tampoco hace `log.error`, así que los fallos reales no dejan rastro.

## Cómo reproducir
`GET /api/communities/no-existe` → 500.

## Arreglo propuesto
Excepciones de dominio con su código HTTP y `log.error` en el genérico (T04).
