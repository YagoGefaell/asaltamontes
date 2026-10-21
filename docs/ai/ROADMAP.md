# Roadmap — orden de implementación

> Prioridades de producto acordadas (2026-10-21): **Chat → Quedadas/eventos → Comunidades**.
> Entorno objetivo actual: **servidor privado de Yago** (docker compose). El hosting gestionado se planificará después de la demo.
> El estado de cada tarea está en su archivo (`tasks/Txx-*.md`); este documento solo define **orden y dependencias**.

## Cómo leer esto

- Las tareas se ejecutan **en el orden de la tabla** salvo que las dependencias permitan paralelizar.
- "Paralelizable con" indica qué puede ir en PRs simultáneas sin pisarse.
- Las tareas marcadas con 🔍 tienen spec de alto nivel: **el primer paso es refinarla** (completar alcance, archivos y criterios) y esa spec refinada va en la propia PR, o en una PR `docs/` previa si cambia el alcance.

## Paso 0 — Hotfix crítico (antes que nada)

| # | Tarea | Depende de | Notas |
|---|---|---|---|
| BUG-001 | [Comprobar participante al enviar mensajes](bugs/BUG-001-chat-send-without-membership.md) | — | PR mínima `fix/BUG-001-chat-membership-check`: solo la comprobación y un test. El resto del endurecimiento del chat va en T09. Puede ir en paralelo con T01; si T01 no está mergeada, el test va en la misma PR con la infraestructura mínima necesaria. |

## Fase 0 — Cimientos (nada de funcionalidades hasta que esto esté)

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T01 | [Infraestructura de tests + CI que los ejecuta](tasks/T01-test-infrastructure.md) | — | T03, T07 | BUG-037 |
| T02 | [Flyway y baseline del esquema actual](tasks/T02-flyway-baseline.md) | T01 | T03, T04, T07 | BUG-005 |
| T03 | [Configuración por perfiles, logs, Actuator](tasks/T03-config-profiles.md) | — | T01, T02, T04 | BUG-004, BUG-026, BUG-029 |
| T04 | [Manejo de errores unificado](tasks/T04-error-handling.md) | T01 | T02, T03 | BUG-010, BUG-011, BUG-036 |
| T05 | [Higiene de entidades, unicidad de username e índices](tasks/T05-entities-indexes.md) | T02 | T06 | BUG-008, BUG-009 |
| T06 | [Endurecimiento de autenticación](tasks/T06-auth-hardening.md) | T02, T04 | T05 | BUG-002, BUG-003, BUG-023, BUG-024, BUG-032, BUG-033 |
| T07 | [Infra del servidor privado: proxy, compose, deploy, backups](tasks/T07-server-infra.md) | — | T01–T06 | BUG-027, BUG-028 |
| T08 | [Actualizar PostgreSQL 13 → 17](tasks/T08-postgres-upgrade.md) | T02, T07 | — | BUG-028 (parte) |

## Fase 1 — Chat (prioridad 1)

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T09 | [Seguridad y corrección del chat](tasks/T09-chat-security.md) | T02, T04 | T10 (apilada) | BUG-001, BUG-016, BUG-017, BUG-018, BUG-019, BUG-021, BUG-022, BUG-030 |
| T10 | [Paginación por cursor del historial](tasks/T10-chat-pagination.md) | T09 | T11 | BUG-020 |
| T11 | [Reconexión WebSocket con token renovado](tasks/T11-ws-reconnect.md) | T09 | T10 | BUG-018 (parte front) |
| T12 | [Gestión de grupos de chat](tasks/T12-group-management.md) | T09 | T13 | — |
| T13 | [Bloqueos y control de quién puede escribirte](tasks/T13-blocking.md) | T09 | T12 | — |

## Fase 2a — Comunidades: base sólida

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T14 | [Corrección de comunidades (slug, ordenación, errores, paginación)](tasks/T14-communities-fixes.md) | T02, T04 | Fase 1 | BUG-012, BUG-013, BUG-014, BUG-015, BUG-035, BUG-045, BUG-046, BUG-047 |

## Fase 3 — Quedadas / eventos (prioridad 2)

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T15 | [Eventos: modelo y API](tasks/T15-events-api.md) | T14 | T17 | — |
| T29a | 🔍 [Sistema de diseño: tokens y componentes base](tasks/T29-design-system.md) | T01 | T15 | — |
| T16 | [Eventos: frontend (lista, detalle con mapa, crear)](tasks/T16-events-frontend.md) | T15, T29a | T17 | — |

## Transversal — necesario para una demo creíble

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T17 | [Almacenamiento de imágenes (sistema de archivos tras `MediaStorage`)](tasks/T17-media-storage.md) | T07 | T15, T16 | — |
| T18 | 🔍 [Notificaciones en tiempo real](tasks/T18-notifications.md) | T09, T15 | T19 | — |

## Fase 2b / 3b — Comunidades y eventos completos

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T19 | 🔍 [Gestión y moderación de comunidades + comunidades privadas](tasks/T19-community-moderation.md) | T14, T18 | T20 | — |
| T20 | 🔍 [Chat de comunidad](tasks/T20-community-chat.md) | T12, T14 | T19 | — |
| T21 | 🔍 [Chat de evento, recordatorios y .ics](tasks/T21-event-chat-reminders.md) | T12, T16, T18 | T22 | — |
| T22 | 🔍 [Muro de comunidad y Home con feed real](tasks/T22-community-feed.md) | T14, T17 | T21 | — |

## Fase 4 — Seguridad de la comunidad, cuentas y demo privada

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T23 | 🔍 [Privacidad de perfil, denuncias y panel de moderación](tasks/T23-privacy-reports.md) | T13, T18 | T24 | BUG-034 |
| T24 | 🔍 [Verificación de email, reset de contraseña, borrar/exportar cuenta](tasks/T24-account-lifecycle.md) | T06 | T23 | BUG-025 |
| T25 | 🔍 [Invitaciones para la demo y páginas legales](tasks/T25-invites-legal.md) | T24 | T26 | — |
| T26 | 🔍 [PWA instalable](tasks/T26-pwa.md) | T07 | T25, T27 | — |
| T27 | 🔍 [Observabilidad (Sentry, métricas, uptime)](tasks/T27-observability.md) | T03 | T26 | — |
| T28 | 🔍 [Pruebas de carga con k6 y tuning](tasks/T28-load-testing.md) | T10, T15, T07 | — | — |

**🏁 Hito: Demo privada** = T01–T28 hechas + checklist de `tasks/T28` cumplido + Yago da el OK.

## Después de la demo (no planificado al detalle)

- Perfeccionar el pipeline (Yago, 2026-10-21): p. ej. imágenes construidas en CI y publicadas en GHCR, deploy por tag/imagen en lugar de build en el servidor, rollback automático si el healthcheck falla, smoke tests tras el deploy, escaneo de dependencias (Dependabot/OWASP). Hasta entonces, T07 solo hace las mejoras mínimas de `deploy.yml`.
- Redis: caché, rate limiting distribuido, relay de STOMP para varias instancias del backend.
- Migración a hosting (VPS público/gestionado), CDN para imágenes (Cloudflare R2).
- Push notifications (Web Push / FCM).
- Monetización: perfiles de marca, posts y eventos patrocinados, analítica para marcas.
- GPX de rutas, integración con Strava.

## Versionado y releases

| # | Tarea | Depende de | Paralelizable con | Cierra bugs |
|---|---|---|---|---|
| T31 | [Versión visible y releases fiables](tasks/T31-versioning.md) | — | cualquiera | — |

Se puede hacer en cualquier momento; conviene antes de la primera release grande desde `dev`.

## Pulido de UI (intercalado)

T29b+ (una PR por pantalla, ver [T29](tasks/T29-design-system.md)): se hacen cuando el límite WIP bloquea PRs de backend o al cerrar cada fase.

## Bugs sueltos (no cubiertos por ninguna tarea)

Se arreglan en PRs `fix/BUG-xxx-*` independientes, intercaladas según severidad (ver `WORKFLOW.md` §8):
BUG-006, BUG-007, BUG-031, BUG-038.
