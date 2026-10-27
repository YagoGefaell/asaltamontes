# T20 — Chat de comunidad

- **Estado:** pendiente
- **Fase:** 2b — Comunidades
- **Depende de:** T12, T14
- **Rama sugerida:** `feat/T20-community-chat`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

## Objetivo
Cada comunidad tiene un chat de grupo propio, sincronizado con su lista de miembros.

## Enfoque
- Reutilizar `Conversation` de tipo `GROUP` con `managed_by = COMMUNITY` (campos preparados en T12). No se pueden añadir ni quitar miembros a mano: entrar o salir de la comunidad sincroniza la conversación.
- Creación perezosa (al primer acceso) o en la creación de la comunidad, más un backfill para las existentes.
- Las silenciadas (`MUTED`) pueden leer pero no escribir; las baneadas pierden el acceso.
- Posibilidad de que la moderación desactive el chat. Mensajes fijados (opcional).
- Para comunidades grandes: la entrega por WS a N miembros no debe hacer N consultas (resolver ids en una sola consulta; vigilar con T28).

## Historial para quien entra (decisión de Yago, 2026-10-27)
En el chat gestionado de comunidad las nuevas participantes ven el **historial completo**; no se aplica el filtro por `joined_at` de los grupos manuales ni la opción "historial visible para nuevas" (ver T12). Test: una usuaria que entra después ve los mensajes anteriores.
