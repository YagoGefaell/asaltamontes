# T11 — Reconexión WebSocket con token renovado

- **Estado:** pendiente
- **Fase:** 1 — Chat
- **Depende de:** T09
- **Rama sugerida:** `fix/T11-ws-reconnect`
- **Cierra bugs:** BUG-018 (parte frontend)
- **Tamaño:** S

## Objetivo
El access token dura 15 minutos. Cuando el WebSocket se reconecta (caída de red, reinicio del backend en un deploy, móvil que vuelve de segundo plano) usa el token en memoria, que puede estar caducado. Con T09 ese CONNECT se rechaza: el cliente debe renovar el token antes de reconectar y recuperar los mensajes perdidos.

## Contexto
- `frontend/src/features/messages/hooks/useWebSocket.js`: `beforeConnect` usa `getAccessToken()` tal cual; `reconnectDelay: 5000` fijo.
- `auth.service.js#refreshTokenRequest`.

## Alcance
### Frontend
- Utilidad `isTokenExpiringSoon(token, marginSeconds = 60)` (decodificar `exp` del JWT sin librerías).
- `beforeConnect` asíncrono: si el token caduca pronto o no existe, `await refreshTokenRequest()`; si falla → logout (callback existente de `apiClient`).
- Ante frame `ERROR` de autenticación: forzar refresh y reintentar una vez; si vuelve a fallar → logout.
- Backoff exponencial con jitter (1 s → 30 s máx.) en vez de 5 s fijos.
- Al reconectar: refrescar la lista de conversaciones y, para el chat abierto, pedir los mensajes posteriores al último id conocido (`GET …/messages?after=<id>`; añadir el parámetro `after` en backend si T10 no lo incluyó).
- Reconectar al volver a primer plano (`visibilitychange`) y al recuperar red (`online`).
- Indicador de estado en la UI del chat: conectado · reconectando · sin conexión (deshabilitar el botón de enviar si no hay conexión, en vez de fallar en silencio).

### Backend
- Si no existe: `GET /api/conversations/{id}/messages?after=<id>` (orden ASC, límite 100).

## Criterios de aceptación
- [ ] Con un access token de vida corta (configurar 1 min en dev), dejar la app abierta 5 min, cortar la red, restaurarla → el chat vuelve a recibir mensajes sin recargar.
- [ ] Mensajes enviados por otra persona durante la desconexión aparecen tras reconectar.
- [ ] Reiniciar el backend (simula deploy) → el cliente reconecta solo.

## Tests requeridos
- Vitest: `isTokenExpiringSoon`, cálculo de backoff.
- Prueba manual documentada en la PR (los pasos de arriba).
