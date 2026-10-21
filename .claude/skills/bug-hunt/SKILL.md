---
name: bug-hunt
description: Análisis sistemático de bugs en un área del código de Asaltamontes (p. ej. chat, auth, community, user, events, frontend-auth, infra). Registra cada hallazgo como docs/ai/bugs/BUG-xxx en una PR docs/. Úsalo cuando el límite WIP impida abrir más PRs de código, después de mergear una funcionalidad grande o cuando Yago lo pida.
---

# Bug hunting

Argumento: área (`auth`, `user`, `community`, `chat`, `events`, `media`, `notifications`, `frontend-<área>`, `infra`, `ci`). Sin argumento: el área con más código cambiado recientemente (`git log --since=2.weeks --name-only origin/dev`) o la menos revisada según `$AI/SESSION_LOG.md`.

## Método
1. **Inventario**: lista los archivos del área (backend y frontend que la consume) y lee también sus tests.
2. **Recorre los flujos de punta a punta** (UI → servicio front → controlador → servicio → repositorio → SQL → respuesta → estado de UI) buscando en este orden:
   - **Autorización**: ¿se comprueba que quien pide es dueña o participante del recurso? ¿Se pueden enumerar ids? ¿Hay datos privados en los DTOs?
   - **Validación de entrada**: nulos, vacíos, longitudes, rangos y enums.
   - **Concurrencia**: read-modify-write, contadores, cupos, unicidad comprobada antes de insertar sin constraint.
   - **Transacciones**: self-invocation de `@Transactional`, efectos laterales antes del commit, `@Modifying` sin transacción, lecturas lazy fuera de la transacción.
   - **Errores**: excepciones que terminan en 500, errores silenciados, `catch` demasiado amplios.
   - **Rendimiento**: listados sin paginar, N+1, consultas sin índice, `LIKE '%x%'`.
   - **Contrato front/back**: nombres de campos, formatos de fecha, paginación ignorada, códigos de estado esperados.
   - **Estado del frontend**: carreras entre peticiones, efectos sin cleanup, estado desincronizado tras errores, optimistic updates sin rollback.
   - **Configuración e infra**: secretos, puertos, CORS, cookies, cabeceras.
3. **Verifica antes de registrar**: compruébalo leyendo el código con atención y, si es barato, con un test o una petición real. Distingue `confirmado` de `probable`. No registres opiniones de estilo como bugs.
4. **Deduplica**: `grep -ril "<palabra clave>" docs/ai/bugs/` y mira las PRs abiertas.

## Registro
- Rama `docs/bugs-<área>-<AAAA-MM-DD>` desde `origin/dev`, en su propio worktree.
- Un archivo por bug con `docs/ai/templates/BUG.md`, numerado de forma consecutiva desde el último de `dev` y de las PRs abiertas.
- Asocia cada bug a una tarea existente si encaja en su alcance (y añádelo a la línea "Cierra bugs" de esa tarea), o déjalo como "ninguna (PR `fix/` propia)".
- Si aparece algo **crítico** (seguridad o pérdida de datos), avísalo a Yago en el mensaje final de la sesión, además de registrarlo.
- PR `docs(bugs): <área> findings (<n> bugs)` con una tabla resumen (ID, severidad, confianza, una línea).
- Anota en `$AI/SESSION_LOG.md` qué área se ha revisado y hasta qué profundidad, para no repetirla.
