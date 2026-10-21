# BUG-027 — El proxy `/api/` de nginx elimina el prefijo y no hay proxy para `/auth` ni el WebSocket

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** infra
- **Tarea que lo resuelve:** T07
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/nginx.conf`

## Qué pasa
`proxy_pass http://backend:8080/;` (con barra final) reescribe `/api/users/me` como `/users/me` → 404. Por eso el frontend apunta directamente al puerto 8080, lo que obliga a exponerlo y a configurar CORS y cookies entre orígenes.

## Cómo reproducir
`curl http://<servidor>/api/users/me` → 404 del backend.

## Arreglo propuesto
Proxy same-origin completo (T07).
