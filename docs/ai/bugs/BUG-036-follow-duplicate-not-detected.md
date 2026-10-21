# BUG-036 — Seguir dos veces no produce `AlreadyFollowingException`

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** probable (verificar con test)
- **Área:** backend/user
- **Tarea que lo resuelve:** T04
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/FollowService.java` → `followUser()`

## Qué pasa
`save()` no hace flush, así que la violación de unique salta en el commit, fuera del `try/catch`. El `catch` nunca se ejecuta y el cliente recibe el 409 genérico "Data integrity violation" en lugar del mensaje específico. (Además, `AlreadyFollowingException` no tiene handler, ver BUG-010.)

## Cómo reproducir
`POST /api/users/follow/B` dos veces.

## Arreglo propuesto
Comprobar `existsBy…` antes o usar `saveAndFlush` dentro del `try` (T04).
