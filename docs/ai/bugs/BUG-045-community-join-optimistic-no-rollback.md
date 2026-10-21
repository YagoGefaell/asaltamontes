# BUG-045 — Unirse/salir de una comunidad: si la petición falla, el rol y el contador no se revierten

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/communities
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, bug hunt de comunidades (Claude)

## Dónde
- `frontend/src/shared/components/CommunityJoinButton.jsx` → `useOptimisticToggle(..., onOptimisticChange)`
- `frontend/src/hooks/useOptimisticToggle.js` → `executeAsync` (en el `catch` solo hace `setState(serverStateRef.current)`)
- `frontend/src/pages/communities/Communities.jsx` y `CommunityProfileDetail.jsx` → `handleToggleCommunity` (cambia `role` y `memberCount` del padre)

## Qué pasa
Al pulsar "Unirse" el botón avisa al padre al instante (`onToggle('MEMBER')`): el padre suma 1 a `memberCount` y pone `role = 'MEMBER'`. Si la petición falla (409, 404, red caída…), el hook vuelve su estado interno a "no miembro", pero **no avisa al padre**: el contador queda con +1 y el `role` del padre sigue en `MEMBER` mientras el botón dice "Unirse". Lo mismo al salir (−1 sin revertir). La interfaz queda incoherente hasta recargar.

## Cómo reproducir
1. Abrir una comunidad con el backend parado (o forzar un 409) y pulsar "Unirse".
2. Tras el error, el contador muestra un miembro más y el botón vuelve a "Unirse".
Test (Vitest): renderizar el botón con un padre que guarde `role`/`memberCount`, mockear `joinCommunityRequest` con rechazo y comprobar que el padre vuelve al estado inicial.

## Arreglo propuesto
Que `useOptimisticToggle` notifique también la reversión (llamar a `onOptimisticChange(serverState)` en el `catch`, o un callback `onRollback`), y que `CommunityJoinButton` la propague a `onToggle`. Revisar los demás usos del hook (follow) para que no cambie su comportamiento.
