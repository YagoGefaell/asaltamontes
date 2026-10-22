# BUG-055 — Un fallo de red o un 5xx al renovar el token cierra la sesión de la usuaria

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código); el impacto real depende de la duración del access token
- **Área:** frontend/auth (afecta al chat y a todo el api-client)
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-22, bug hunt de frontend-chat (Claude)

## Dónde
- `frontend/src/features/auth/services/auth.service.js:122-133` (`refreshTokenRequest`: `catch { return false }` para cualquier error).
- `frontend/src/lib/api-client.js:93-107` (`_performRefresh`: `false` o excepción → `_handleLogout()`).
- `frontend/src/features/messages/hooks/useWebSocket.js:45-49` (reconexión: `refreshSession()` si el token está por caducar).

## Qué pasa
No se distingue "el servidor rechazó el refresh token" (401/403: sesión realmente caducada) de "no se pudo hablar con el servidor" (red, timeout, 502 de nginx durante el despliegue, 500). Ambos terminan en `_handleLogout()`. Como cada push a `main` redespliega el backend, y T11 reconecta el WebSocket renovando antes el token, una usuaria cuyo access token caduque durante el corte (o al despertar el portátil con `navigator.onLine` ya en `true` pero la red aún sin servicio) es expulsada al login aunque su refresh token sea válido. Lo mismo para cualquier petición REST que reciba un 401 justo cuando la API no responde.

## Cómo reproducir
1. Iniciar sesión; esperar a que el access token esté a punto de caducar (o simularlo reduciendo su duración).
2. Parar el backend y provocar una reconexión del WebSocket (o una petición REST): el refresh falla por red y la app muestra el login.
Test (Vitest): `fetch` rechazado en `/auth/refresh` y comprobar que no se llama a `logoutCallback`.

## Arreglo propuesto
`refreshTokenRequest` debe distinguir un resultado "rechazado" (401/403 del endpoint de refresh) de un error transitorio (red/timeout/5xx). `_performRefresh` solo hace logout en el primer caso; en el segundo propaga el error (la petición falla; el WebSocket reintenta con su backoff).
