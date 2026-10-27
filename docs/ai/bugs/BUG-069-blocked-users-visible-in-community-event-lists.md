# BUG-069 — Las usuarias bloqueadas siguen apareciendo en listados de comunidades, eventos y grupos

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** confirmado
- **Área:** backend/community · backend/event · backend/message
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-27, al implementar T13 (lectura de código)

## Dónde
`community/repository/CommunityMemberRepository.java` (miembros), `event/repository/EventAttendeeRepository.java` (asistentes), `message/repository/ConversationParticipantRepository.java#findParticipantPage` (participantes de grupo).

## Qué pasa
T13 oculta a quien bloqueó en el perfil, la búsqueda y las listas de seguidores, pero no en los listados de miembros de comunidad, asistentes a eventos ni participantes de grupo. Desde ahí la bloqueada puede ver nombre y foto de quien la bloqueó y llegar a su perfil (que sí da 404). En los grupos compartidos es intencionado que sigan juntas (T13 no expulsa a nadie), pero el resto de listados podría filtrar.

## Cómo reproducir
A bloquea a B; ambas son miembros de la misma comunidad pública; B abre la lista de miembros y ve a A.

## Arreglo propuesto
Reutilizar la cláusula `NOT EXISTS (UserBlock ...)` de `UserAccountRepository.searchUsersByUsername` en las consultas de miembros y asistentes. Decidir con Yago si en grupos compartidos se oculta (probablemente no).

## Resolución
Las consultas `CommunityRepository#findMembersExcept` y `EventAttendeeRepository#findAttendees` (y su `countQuery`) llevan ahora el `NOT EXISTS (UserBlock ...)` en ambas direcciones, así que filtran en la propia consulta: sin N+1 y con `totalElements`/páginas coherentes. En asistentes, la propia usuaria siempre se ve a sí misma. Los **participantes de grupo no se filtran**: T13/D-022 deja a las bloqueadas juntas en los grupos compartidos (nadie es expulsada y los mensajes siguen visibles); ocultarlas solo en la lista de participantes sería incoherente con los mensajes. Test: `BlockedInListsIT`.
