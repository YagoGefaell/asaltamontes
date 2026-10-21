# BUG-003 — El logout no invalida el refresh token

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/auth/controller/AuthController.java` → `logout()`

## Qué pasa
El logout solo pide al navegador que borre la cookie. El refresh token sigue siendo válido 7 días: si se ha filtrado, la sesión no se puede cortar. Tampoco existe "cerrar sesión en todos los dispositivos".

## Cómo reproducir
Guardar el refresh token, hacer logout y llamar a `/auth/refresh` con la cookie antigua → 200.

## Arreglo propuesto
Refresh tokens persistidos y revocables, con rotación (T06a).
