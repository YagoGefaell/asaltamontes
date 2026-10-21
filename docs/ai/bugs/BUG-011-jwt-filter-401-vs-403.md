# BUG-011 — Token caducado o inválido devuelve 403 en lugar de 401

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth + frontend
- **Tarea que lo resuelve:** T04
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/security/JwtAuthFilter.java`, `SecurityConfig.java`; `frontend/src/lib/api-client.js`

## Qué pasa
Sin `AuthenticationEntryPoint`, una petición anónima a una ruta protegida recibe 403. Además, `JwtAuthFilter` lanza `InvalidTokenException` dentro del filtro, fuera del alcance del `@RestControllerAdvice`. Como consecuencia, el frontend refresca el token ante **cualquier** 403, también en denegaciones de permiso legítimas.

## Cómo reproducir
Llamar a `/api/users/me` con un token caducado → 403.

## Arreglo propuesto
Entry point 401 + access denied handler 403 en JSON; el frontend refresca solo ante 401 (T04).
