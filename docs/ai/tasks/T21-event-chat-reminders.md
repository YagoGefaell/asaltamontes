# T21 — Chat de evento, recordatorios y exportación a calendario

- **Estado:** pendiente
- **Fase:** 3b — Eventos
- **Depende de:** T12, T16, T18
- **Rama sugerida:** `feat/T21-event-chat-reminders`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Coordinar la quedada ("llego 5 min tarde", "¿hay barro?") y que nadie se olvide de ella.

## Enfoque
- Chat de evento: `Conversation` `GROUP` con `managed_by = EVENT`, sincronizado con las asistentes `GOING` (las de la lista de espera, opcional). Se archiva (solo lectura) 7 días después del evento.
- Recordatorios: job programado (cada 5 min) que envía `EVENT_REMINDER` 24 h y 2 h antes a las `GOING`, de forma idempotente (tabla o columna `reminder_sent_*`). Con varias instancias del backend hará falta ShedLock; con una basta con documentarlo.
- `GET /api/events/{id}.ics` y botón "Añadir al calendario".
- Cancelar o cambiar la hora de un evento → mensaje de sistema en su chat + notificación.

## Historial para quien entra (decisión de Yago, 2026-10-27)
En el chat gestionado de quedada las nuevas participantes ven el **historial completo**; no se aplica el filtro por `joined_at` de los grupos manuales ni la opción "historial visible para nuevas" (ver T12). Test: una usuaria que entra después ve los mensajes anteriores.
