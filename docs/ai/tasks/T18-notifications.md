# T18 — Notificaciones en tiempo real

- **Estado:** pendiente
- **Fase:** Transversal
- **Depende de:** T09, T15
- **Rama sugerida:** `feat/T18-notifications`
- **Cierra bugs:** —
- **Tamaño:** L
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
La página `Notifications.jsx` es un mock. Hacen falta notificaciones reales dentro de la app (las push quedan para después de la demo).

## Enfoque
- Tabla `notifications` (recipient_id, type, actor_id, target_type, target_id, payload jsonb, read_at, created_at) + índice `(recipient_id, created_at DESC)` y otro parcial para las no leídas.
- Tipos iniciales: `NEW_FOLLOWER`, `EVENT_JOINED` (a la organizadora), `EVENT_UPDATED`, `EVENT_CANCELLED`, `WAITLIST_PROMOTED`, `EVENT_REMINDER` (T21), `COMMUNITY_JOIN_REQUEST` y `COMMUNITY_REQUEST_ACCEPTED` (T19), `ADDED_TO_GROUP`.
- Generación desacoplada: los servicios publican eventos de dominio y un `NotificationService` los escucha con `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`. Agregación ("Ana y 3 más te siguen") opcional.
- Entrega en tiempo real por `/user/queue/notifications` y contador de no leídas en la `NavBar`.
- API: `GET /api/notifications` (cursor), `POST /api/notifications/read` (ids o todas), `GET /api/notifications/unread-count`.
- Preferencias por tipo en Ajustes → Notificaciones (la ruta ya existe en `constants.js`).
- Retención: borrar las leídas con más de 90 días.
