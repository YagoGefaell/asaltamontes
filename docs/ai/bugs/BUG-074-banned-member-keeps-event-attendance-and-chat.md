# BUG-074 — Una expulsada de la comunidad sigue apuntada a sus quedadas y en su chat

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable
- **Área:** backend/event, backend/community
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-27, al implementar T21

## Dónde
`community/service/CommunityModerationService.java` (expulsión) y `event/service/EventService.java` (`canView`, `join`, `leave`); `message/service/EventChatService.java` (`open`).

## Qué pasa
Expulsar a alguien de una comunidad (`BANNED`) no toca sus filas de `event_attendees`. Si estaba `GOING` en una quedada `COMMUNITY` de esa comunidad, sigue contando como asistente, conserva su participación en el chat de la quedada (T21) y recibe notificaciones y recordatorios, aunque `canView` ya le devuelve 404 al abrir el evento. Su sitio tampoco se libera para la lista de espera.

## Cómo reproducir
1. Propietaria crea una quedada `COMMUNITY`; una miembra se apunta.
2. La propietaria la expulsa de la comunidad.
3. `GET /api/events/{id}/chat` con la expulsada: `200`, y sigue leyendo y escribiendo (el 404 de `canView` no se aplica en `EventChatService.open`/`requireCanWrite`).

## Arreglo propuesto
Al expulsar, cancelar sus asistencias a quedadas `COMMUNITY` de esa comunidad (liberando plaza y promoviendo la lista de espera) y quitarla de sus chats; en `open` exigir además `canView`.
