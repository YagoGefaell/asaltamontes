# Recomendaciones de automatización de Claude Code (2026-10-22)

Análisis hecho con el plugin `claude-code-setup` (skill `claude-automation-recommender`) y evaluado contra nuestra configuración actual. **Nada de esto está aplicado: decide Yago.** Cada punto lleva veredicto: ✅ recomendado, 🟡 opcional, ❌ descartado.

## Perfil del proyecto

- Backend Java 21 / Spring Boot 3.5 / PostgreSQL 17 / Flyway / Testcontainers. Frontend React 19 + Vite 7 + Vitest + ESLint 9.
- CI y deploy con GitHub Actions (runner self-hosted en una Raspberry Pi 5, 4 GB de RAM, microSD).
- Config actual de Claude Code: `CLAUDE.md` (~5 KB), 6 skills del proyecto (~18 KB), 2 agentes (`implementer`, `bug-hunter`), `.claude/settings.json` solo con permisos (allow/deny). Sin hooks, sin `.mcp.json`.
- Ya instalados a nivel de usuario (no del repo): MCP `context7` y el plugin `claude-code-setup`.

## Criterios de evaluación

1. **Contexto por petición**: un MCP añade las definiciones de sus herramientas a *cada* petición; un hook, un skill o un agente no (solo se cargan al usarse). Cuota del plan de Yago limitada.
2. **RAM de la Pi** (4 GB, WORKFLOW §10, D-016): nada que deje procesos residentes pesados (navegadores, servidores Node/Java adicionales).
3. **Reglas ya fijadas**: D-012 (no tocar la autenticación), nada de merge ni push a `main`, no tocar producción.

## MCP servers

| Propuesta | Veredicto | Motivo |
|---|---|---|
| **context7** (docs de librerías) | ✅ ya instalado | Spring Boot 3.5, Flyway, React 19, Router 7, Vite 7, Vitest 5, stompjs: versiones recientes donde la memoria del modelo falla. Remoto, sin RAM local. Coste: definiciones de 2 herramientas por petición (pequeño). Uso: solo al escribir código contra una API de librería, no para lógica de negocio. |
| **GitHub MCP** | ❌ | `gh` ya está autenticado y permitido en `settings.json`; el MCP duplicaría herramientas (decenas de definiciones en cada petición) con un token con más alcance. |
| **Playwright MCP** | ❌ por ahora | Útil para probar el frontend (T14b, T16 quedaron "sin probar en navegador"), pero necesita Chromium: ~400-800 MB de RAM en una Pi de 4 GB que además aloja producción. Alternativa: pruebas Vitest + Testing Library (ya están) y prueba manual de Yago. Revisar si algún día hay un SSD y más RAM, o un portátil. |
| **PostgreSQL / Docker MCP** | ❌ | El clasificador de permisos ya deniega leer la BD de producción; un MCP de BD la expondría. Para el stack `-dev` basta `docker compose -p asaltamontes-dev exec`. El MCP de Docker toca contenedores de producción: choca con una regla innegociable. |
| **Sentry / Slack / Linear / AWS / Supabase** | ❌ | No usamos ninguno. |

## Hooks

Hoy no hay ninguno. Los hooks no cuestan contexto; solo ejecutan un comando local.

| Propuesta | Veredicto | Detalle |
|---|---|---|
| **PreToolUse: bloquear edición de migraciones Flyway ya mergeadas** (`backend/src/main/resources/db/migration/V*.sql` que existan en `origin/main`) | ✅ | Regla innegociable de `CLAUDE.md`; BUG-049 nació justo de editar V4 *antes* de mergear (permitido) y de no mirar prod. El hook debe permitir los ficheros nuevos y bloquear los que ya están en `origin/main`. Evita un error caro (checksum de Flyway roto en producción). Script de ~10 líneas en `scripts/hooks/`. |
| **PreToolUse: bloquear escritura de `.env`, `/etc/github-runner.env` y `.ai/` fuera de las skills** | 🟡 | Hoy `.env` está en `.gitignore` y los secretos no están en el repo. Un `deny` en `settings.json` (`Edit(.env)`, `Read(.env)`) es más simple que un hook y basta: preferirlo. |
| **PostToolUse: `eslint --fix` / formateo tras editar JS/JSX** | ❌ | `npm run lint` ya es parte del DoD y de CI; ejecutarlo tras cada edición suma segundos de CPU en la Pi y ruido. Mejor al final, como ya se hace. |
| **PostToolUse: compilar Java tras cada edición** | ❌ | `./mvnw` tarda decenas de segundos en la Pi y mantiene la JVM ocupada; se compila y prueba al terminar la unidad, como ahora. |
| **Stop/Notification: aviso de que acabó la sesión** | ❌ | Ya hay `PushNotification`/notificaciones de Claude Code configuradas a nivel de usuario. |

## Skills

Hoy: `start-session`, `end-session`, `do-task`, `address-review`, `bug-hunt`, `autopilot`. Cubren el flujo completo.

| Propuesta | Veredicto | Detalle |
|---|---|---|
| **`new-migration`** (solo usuaria/Claude explícito, `disable-model-invocation: true`) | ✅ | Pasos fijos: siguiente número `V<n>`, buscar objetos previos por columna en `pg_constraint`/`pg_index` (nunca por nombre de V1), probar en PG desechable con el nombre renombrado de prod, línea de DoD de WORKFLOW §6. Condensa la lección de BUG-049 en un procedimiento. Solo se carga al invocarlo. |
| **`verify-pr`** (checklist previa a abrir PR: `./mvnw verify`, `npm run lint && build && test`, contrato `api-contract-v1.json`, BUG-xxx registrados) | 🟡 | Ya está repartido entre `do-task` y WORKFLOW §6. Solo tendría sentido si se ve que se olvidan pasos; si no, duplica. |
| **`frontend-design`** (plugin oficial) | 🟡 | Encaja con T29 (sistema de diseño, D-015) cuando se aborde la UI; no antes. No instalar ahora. |
| `api-doc` / `gen-test` / `release-notes` | ❌ | El contrato OpenAPI se mantiene a mano en la PR (y BUG-050 pide arreglarlo, no automatizarlo); los tests ya forman parte de `do-task`; los releases los gestiona Yago (`release.yml`). |

## Subagentes

Hoy: `implementer` y `bug-hunter`.

| Propuesta | Veredicto | Detalle |
|---|---|---|
| **`security-reviewer`** (solo lectura: revisa el diff de una PR en auth, WebSocket, subida de archivos T17 y privacidad T23) | ✅ | Hay superficie sensible creciente (T17 medios, T19 moderación, T23 privacidad) y D-012 exige cuidado con auth. Un agente de solo lectura (`Read`, `Grep`, `Bash` limitado a `git diff`) con checklist (autorización por recurso, IDOR, validación de entrada, rate limiting, logs con datos personales). Se lanza a mano antes de abrir PRs sensibles; coste cero si no se usa. |
| **`migration-reviewer`** | 🟡 | Podría cubrirlo la skill `new-migration` + `/code-review`. Solo si se acumulan migraciones. |
| `test-writer`, `ui-reviewer`, `api-documenter`, `performance-analyzer` | ❌ | Los tests los escribe `implementer`; `performance` ya se hace con EXPLAIN en cada PR (T10b); lanzar más agentes choca con el máximo de 2 por RAM (D-016). |

## Plugins

| Propuesta | Veredicto | Detalle |
|---|---|---|
| `claude-code-setup` | ✅ ya instalado | Solo lectura; ya cumplido su cometido. |
| `code-review`, `security-review` (skills integradas) | ✅ ya disponibles | Usar `/code-review` antes de pasar a lista PRs grandes, a criterio de Yago (consume cuota). |
| `commit-commands`, `feature-dev`, `hookify` | ❌ | Duplican el flujo de `do-task`/WORKFLOW y añaden texto de skills sin beneficio. |

## Otras observaciones sobre la configuración actual

- **`.claude/settings.json`**: los permisos de `allow` cubren el flujo, pero no hay `deny` de lectura para secretos. Propuesta mínima: añadir `Read(.env)`, `Read(/etc/github-runner.env)` a `deny`. Coste cero.
- **`CLAUDE.md` (~5 KB, cargado siempre)**: es razonable. No añadir más texto; lo específico va en skills (se carga bajo demanda). Si crece, mover "Stack y estructura" a `docs/ai/`.

## Resumen para decidir (por orden de valor/coste)

1. ✅ Hook PreToolUse que proteja las migraciones Flyway mergeadas.
2. ✅ Skill `new-migration` (solo invocación explícita).
3. ✅ Subagente de solo lectura `security-reviewer`.
4. 🟡 `deny` de lectura de `.env` y `/etc/github-runner.env` en `settings.json`.
5. ❌ Descartados por contexto/RAM: GitHub MCP, Playwright MCP, BD/Docker MCP, hooks de lint/compilación por edición.

Si Yago aprueba alguno, se implementa en una PR `chore/` aparte (uno por cambio, con el diff mínimo).
