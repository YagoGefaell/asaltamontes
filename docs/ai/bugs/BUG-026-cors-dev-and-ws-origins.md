# BUG-026 — CORS: el modo de desarrollo con Vite no funciona y el WebSocket admite cualquier origen

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/config
- **Tarea que lo resuelve:** T03
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/config/CorsConfig.java` + `application.properties` (por defecto `http://localhost,http://127.0.0.1`); `backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/config/WebSocketConfig.java` (`setAllowedOriginPatterns("*")`)

## Qué pasa
Los orígenes ya son configurables (commit 50813e2), pero el valor por defecto no incluye `http://localhost:5173`, así que el flujo `npm run dev` del README falla por CORS. El endpoint WebSocket acepta cualquier origen.

## Cómo reproducir
`cd frontend && npm run dev` con el backend en local → error de CORS en el login.

## Arreglo propuesto
Por defecto en `dev` con `:5173` y mismos orígenes para el WS (T03); a medio plazo, mismo origen vía proxy (T07).
