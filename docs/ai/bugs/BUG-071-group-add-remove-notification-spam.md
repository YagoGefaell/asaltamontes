# BUG-071 — Añadir y quitar a alguien de un grupo en bucle genera notificaciones sin límite

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** probable
- **Área:** backend/message
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-28, security-reviewer de la PR #88

## Dónde
`GroupService.addParticipants` / `removeParticipant`

## Qué pasa
Una administradora puede añadir y expulsar a la misma usuaria repetidamente y cada alta publica `AddedToGroup` (notificación `ADDED_TO_GROUP` + mensaje de sistema). No hay límite por usuaria para esta acción (sí lo hay para crear grupos, T18).

## Cómo reproducir
Bucle de `POST .../participants` y `DELETE .../participants/{username}` sobre el mismo grupo y usuaria.

## Arreglo propuesto
Límite por usuaria en `UserActionLimiter` para añadir participantes, y/o deduplicar `ADDED_TO_GROUP` no leída por grupo y destinataria.
