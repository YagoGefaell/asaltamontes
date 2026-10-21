# T13 — Bloqueos y control de quién puede escribirte

- **Estado:** pendiente
- **Fase:** 1 — Chat
- **Depende de:** T09
- **Rama sugerida:** `feat/T13-blocking`
- **Cierra bugs:** —
- **Tamaño:** M

## Objetivo
La plataforma se presenta como un **espacio seguro para mujeres**: los mensajes privados de desconocidos son el principal vector de acoso. Una usuaria debe poder bloquear a otra y decidir quién puede iniciarle un chat privado.

## Alcance
### Backend
- Migración: tabla `user_blocks (blocker_id, blocked_id, created_at)`, PK compuesta, índice por `blocked_id`. Columna `user_profiles.dm_policy` (`EVERYONE` | `FOLLOWING` | `MUTUALS` | `NOBODY`, por defecto `EVERYONE`). Decidido por Yago (D-018): por defecto `EVERYONE`.
- `POST/DELETE /api/users/{username}/block`, `GET /api/users/me/blocks` (paginado).
- Efectos de un bloqueo (en cualquiera de los dos sentidos):
  - No se puede crear ni usar la conversación privada entre ambas (enviar → 403 con mensaje neutro).
  - Se deshacen los follows en ambos sentidos (y se corrigen los contadores).
  - La bloqueada no ve el perfil de quien la bloqueó (404) ni aparece en sus búsquedas o listados de seguidores.
  - En grupos compartidos **no** se expulsa a nadie (los mensajes se siguen viendo; documentarlo).
  - No se puede añadir a alguien a un grupo si te ha bloqueado.
- `dm_policy` se comprueba al **crear** una conversación privada nueva (las existentes siguen funcionando salvo bloqueo).
- Servicio `BlockService.isBlockedEitherWay(a, b)` reutilizable por T19, T22 y T23.

### Frontend
- Menú "⋯" en el perfil de otra usuaria: Bloquear / Desbloquear (con confirmación).
- Ajustes → Privacidad: "Quién puede escribirme" y la lista de bloqueadas.
- En el chat: si el envío falla por bloqueo o por política, mostrar un aviso claro.

## Criterios de aceptación
- [ ] Tras bloquear, ninguna de las dos puede enviar mensajes privados a la otra.
- [ ] La bloqueada no encuentra a la bloqueadora en la búsqueda ni puede ver su perfil.
- [ ] Con `dm_policy = FOLLOWING`, alguien a quien no sigo no puede iniciarme un chat.
- [ ] Los contadores de follows quedan correctos tras el bloqueo.

## Tests requeridos
- IT de cada efecto listado.
