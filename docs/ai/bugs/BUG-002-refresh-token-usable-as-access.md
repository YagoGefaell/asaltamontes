# BUG-002 — El refresh token sirve como access token y viceversa

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/security/JwtUtil.java`, `JwtAuthFilter.java`, `AuthService.refreshAccessToken()`

## Qué pasa
Ambos tokens son JWT firmados con la misma clave y sin claim de tipo. Un refresh token (7 días) enviado como `Authorization: Bearer` autentica cualquier petición, lo que anula la ventaja de que el access token caduque a los 15 minutos. Un access token también vale para `/auth/refresh`.

## Cómo reproducir
Copiar el valor de la cookie `refreshToken` y usarlo como `Bearer` en `GET /api/users/me` → 200.

## Arreglo propuesto
Claim `typ` y validación por tipo; a medio plazo, refresh token opaco guardado con hash (T06a).
