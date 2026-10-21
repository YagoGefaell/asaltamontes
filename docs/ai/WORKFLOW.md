# Flujo de trabajo

Este documento define cómo trabaja Claude (y sus agentes) en este repositorio. Yago es el único revisor y quien mergea.

## 1. Unidad de trabajo

- Una **tarea** (`docs/ai/tasks/Txx-*.md`) o un **bug** (`docs/ai/bugs/BUG-xxx-*.md`) = una rama = una PR.
- Si una tarea es grande (tamaño `L`), se divide en varias PRs: `T15a`, `T15b`… Cada PR debe ser revisable en **≤ 30 min** (orientativo: < 600 líneas cambiadas sin contar tests ni lockfiles).
- Una PR **no mezcla** refactors no pedidos, bugs ajenos ni cambios de formato masivos.

## 2. Ramas

Formato: `<tipo>/<ID>-<slug-corto>`

| tipo | uso |
|---|---|
| `feat` | funcionalidad nueva |
| `fix` | corrección de bug |
| `refactor` | cambio interno sin cambio de comportamiento |
| `perf` | rendimiento |
| `test` | solo tests |
| `ci` / `chore` | pipeline, tooling, dependencias |
| `docs` | solo documentación (incluye registrar bugs nuevos) |

Ejemplos: `feat/T15-events-api`, `fix/BUG-006-follow-recount-tx`, `docs/bugs-chat-2026-10-21`.

### Base de la rama
- Por defecto, desde `origin/main` actualizado: `git fetch && git switch -c <rama> origin/main`.
- **PRs apiladas**: si la tarea depende de una PR aún no mergeada, crea la rama desde esa rama, abre la PR con `--base <rama-padre>` y escribe `Depende de #N` en la descripción. Cuando la padre se mergee: `git rebase --onto origin/main <rama-padre> <rama>` y cambia la base de la PR a `main`.
- No apiles más de 2 niveles.

### Trabajo en paralelo (agentes)
- Cada agente trabaja en su **propio git worktree** (no cambies de rama en el checkout principal mientras otro agente lo usa):
  `git worktree add ../asaltamontes-wt/<rama> -b <rama> origin/main`
- Los tests usan Testcontainers (puertos aleatorios) → no hay colisiones. **No levantes `docker compose` del stack completo en paralelo** desde varios worktrees (puertos fijos 80/8080/5433).
- Al terminar: `git worktree remove ../asaltamontes-wt/<rama>`.

## 3. Commits

- [Conventional Commits](https://www.conventionalcommits.org/) en inglés: `feat(chat): paginate message history by cursor`.
  El workflow `release.yml` calcula la versión a partir de estos prefijos (`feat` → minor, `fix` → patch, `BREAKING CHANGE` → major).
- Scope = dominio: `auth`, `user`, `community`, `chat`, `events`, `media`, `infra`, `ci`, `docs`, `front`…
- Commits pequeños y con sentido; no es necesario un solo commit (Yago hará **squash merge**, el título de la PR será el mensaje final).
- Termina cada mensaje de commit con la línea de co-autoría indicada por el sistema.

## 4. Pull Requests

- Título = mensaje conventional commit final: `feat(events): event model, API and capacity handling (T15)`.
- Cuerpo según `docs/ai/templates/PR_BODY.md` (también en `.github/pull_request_template.md`).
- Creación: `gh pr create --base main --title "..." --body-file <archivo>`.
  Si `gh` no está disponible: `git push -u origin <rama>`, guarda el cuerpo en `.ai/pr-bodies/<rama>.md` y deja en `.ai/STATUS.md` el enlace
  `https://github.com/YagoGefaell/asaltamontes/compare/main...<rama>?expand=1` para que Yago la abra.
- **Límite WIP**: como máximo **4 PRs abiertas sin revisar**. Si se alcanza, dedica la sesión a bug hunting, refinar specs de tareas futuras o atender feedback.
- La PR marca en el propio diff el `Estado` de la tarea/bugs que cierra (`hecho`/`resuelto`) para que `main` refleje la verdad tras el merge.

## 5. Revisión y feedback

- Al inicio de cada sesión se leen los comentarios de las PRs abiertas (`/address-review`).
- Cada comentario de Yago se responde: o se aplica (commit nuevo en la misma rama, **sin force-push** salvo rebase necesario) o se argumenta por qué no.
- Si un comentario de Yago expresa una preferencia general ("siempre…", "nunca…"), se añade a `docs/ai/DECISIONS.md` o a `CLAUDE.md` en una PR `docs/`.
- PR rechazada/cerrada → anotar el motivo en `.ai/SESSION_LOG.md` y en la tarea.

## 6. Definition of Done (checklist de cada PR)

- [ ] Cumple todos los **criterios de aceptación** de la tarea/bug.
- [ ] Tests nuevos: backend con JUnit + Testcontainers (integración) y/o unitarios; frontend con Vitest cuando aplique. Un bug arreglado incluye un test que **falla sin el arreglo**.
- [ ] `cd backend && ./mvnw verify` en verde.
- [ ] `cd frontend && npm run lint && npm run build` (y `npm test` cuando exista) en verde.
- [ ] Si hay cambio de esquema: migración Flyway nueva, probada sobre una BD con datos (no solo vacía).
- [ ] Si cambia la API: actualizado `docs/api-contract-v1.json` y el servicio del frontend correspondiente.
- [ ] Variables de entorno nuevas documentadas (`.env.example`, compose, README).
- [ ] Autorrevisión del diff hecha (puedes usar `/code-review`) — sin código muerto, `console.log` de debug ni TODOs sin bug asociado.
- [ ] `Estado` de la tarea/bugs actualizado en el diff.
- [ ] Sección "Cómo probarlo" de la PR con pasos manuales reproducibles.

## 7. Bugs

- Todo bug detectado (al leer código, al implementar o reportado por Yago) se registra en `docs/ai/bugs/BUG-xxx-<slug>.md` con la plantilla.
- Numeración: siguiente número libre mirando `main` **y** las PRs abiertas.
- Registro de bugs nuevos sin arreglo: PR `docs/bugs-<area>-<fecha>` (se pueden agrupar varios).
- Si el bug se arregla dentro de una tarea, el archivo del bug se crea/actualiza en esa misma PR.
- Severidad: **crítica** (seguridad/pérdida de datos, se ataca ya, salta el orden del roadmap) · **alta** (funcionalidad principal rota) · **media** · **baja**.
- Confianza: **confirmado** (reproducido o test) · **probable** (lectura de código; el arreglo debe empezar por un test que lo reproduzca; si no se reproduce, se cierra como `descartado`).

## 8. Prioridad al elegir trabajo

1. Feedback pendiente en PRs abiertas.
2. Bugs **críticos** sin PR.
3. Siguiente tarea del `ROADMAP.md` cuyas dependencias estén mergeadas (o en PR, apilando).
4. Bugs **altos** sin tarea asociada.
5. Bug hunting (`/bug-hunt <área>`) o refinamiento de specs de tareas futuras.

## 9. Estados

- Tarea: `pendiente` → `en-curso` → `en-revisión (PR #N)` → `hecho` · también `bloqueada (motivo)`.
- Bug: `abierto` → `en-curso` → `en-revisión (PR #N)` → `resuelto` · también `descartado (motivo)`.
- En los archivos versionados solo se escribe `pendiente`/`abierto` o `hecho`/`resuelto`/`descartado`; los estados intermedios viven en `.ai/STATUS.md`.

## 10. Trabajar en el servidor de casa (misma máquina que la app desplegada)

Claude y sus agentes trabajan en el servidor de la LAN de Yago, donde también corren el runner self-hosted y la app desplegada (proyecto de compose de `main`).

- **Producción es intocable a mano**: nunca `docker compose down/rm/restart`, ni borrar volúmenes, ni tocar la BD del proyecto desplegado. Solo cambia vía merge → `deploy.yml`. (Bloqueado también en `.claude/settings.json`.)
- **El checkout que usa el runner** (`_work/` del runner) no se toca. Claude trabaja en su propio clon (p. ej. `~/work/asaltamontes`) y en worktrees `~/work/asaltamontes-wt/<rama>`.
- **Pruebas manuales del stack**: `docker compose -p asaltamontes-dev -f docker-compose.yml -f docker-compose.dev.yml up -d` con puertos alternativos (frontend 8081, backend 8091, BD 5434) y volúmenes propios del proyecto `-dev`. El override `docker-compose.dev.yml` se crea en T07. Solo un stack `-dev` a la vez; bájalo al terminar (`docker compose -p asaltamontes-dev down`; esto lo hace Yago o se pide permiso puntual, porque `down` está en la lista de denegados).
- **Recursos**: no lances más de 3 agentes en paralelo con tests de Testcontainers (CPU y RAM compartidas con la app desplegada).
- **Tras cada merge**: si hay acceso, verifica que el deploy terminó (`gh run list --workflow deploy.yml -L 1`) y que `/actuator/health` responde. Si falla, avisa en STATUS como bloqueo prioritario; no lo arregles tocando producción.
