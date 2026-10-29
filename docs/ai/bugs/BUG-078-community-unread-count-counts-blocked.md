# BUG-078 — `unreadCount` de `/api/communities/me` cuenta mensajes de usuarias bloqueadas

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado
- **Área:** backend/community
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-28, revisión de seguridad de la PR #116 (T29i/T31c)

## Qué pasa
El último mensaje de una usuaria bloqueada (D-022) se oculta en la tarjeta, pero `chat.unreadCount` sigue contando sus mensajes. El contador no coincide con lo que se ve (en el cliente se pliegan, D-022 §6) y puede revelar que hay actividad de esa usuaria.

## Arreglo propuesto
Excluir de `unreadCount` los mensajes de usuarias bloqueadas en cualquier dirección, o documentar que el contador es del chat completo, y alinear con el contador de la lista de chats.
