# T33 — Comunidades verificadas

- **Estado:** pendiente (🔍 spec de alto nivel: refinar antes de implementar)
- **Fase:** después de T23 (confianza)
- **Depende de:** T23 (panel de moderación de la app)
- **Rama sugerida:** `feat/T33-verified-communities`
- **Cierra bugs:** —
- **Tamaño:** M

## Objetivo
Que las usuarias sepan qué comunidades son de fiar (clubes, marcas o grupos reales y reconocidos), igual que el check de Instagram.

## Decisiones de Yago (2026-10-29)
- **Un solo sello: «Verificada»**, sin tipos ni categorías. Si es de una zona, ya se ve por su ciudad.
- **Proceso**: la propietaria la **solicita** desde los ajustes de la comunidad (con un motivo o enlace: web, Instagram del club…), y una **admin de Asaltamontes la aprueba o rechaza** en el panel de moderación (T23).
- **Se pierde** si la comunidad cambia de propietaria o de nombre (vuelve a solicitarse).
- Cuenta como señal en las recomendaciones ([T32](T32-recommendations.md)).

## Enfoque (a refinar)
- Migración: `communities.verified_at` (null = no verificada) y una tabla de solicitudes (`community_verification_requests`: comunidad, solicitante, motivo, enlace, estado, quién resolvió, cuándo), con `public_id`.
- Endpoints: solicitar (propietaria; una pendiente a la vez y cooldown tras un rechazo), listar y resolver (admin de la app), y `verified` en los DTO de comunidad (filas, perfil, búsqueda).
- Quitar la verificación al cambiar propietaria o nombre, en la misma transacción; dejarlo en el registro de moderación.
- Frontend: icono de verificada junto al nombre en todas partes donde sale una comunidad (con `aria-label` «Comunidad verificada» y explicación al tocarlo); formulario de solicitud en los ajustes de la comunidad (patrón de formularios de `DESIGN.md`); cola en el panel de admin.

## Preguntas abiertas (para el refinado)
- Cooldown tras un rechazo (¿30 días?) y si se avisa a la propietaria por notificación (T18).
- Si una admin puede verificar directamente sin solicitud.

## Fuera de alcance
- Verificación de usuarias o marcas (perfiles de marca: monetización, después de la demo).
