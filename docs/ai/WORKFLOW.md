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
- Por defecto, desde `origin/dev` actualizado: `git fetch && git switch -c <rama> origin/dev`.
- Excepción: **hotfix** de algo roto en producción → desde `origin/main` (ver §11).
- **PRs apiladas**: si la tarea depende de una PR aún no mergeada, crea la rama desde esa rama, abre la PR con `--base <rama-padre>` y escribe `Depende de #N` en la descripción. Cuando la padre se mergee: `git rebase --onto origin/main <rama-padre> <rama>` y cambia la base de la PR a `main`.
- No apiles más de 2 niveles.

### Trabajo en paralelo (agentes)
- Cada agente trabaja en su **propio git worktree** (no cambies de rama en el checkout principal mientras otro agente lo usa):
  `git worktree add ../asaltamontes-wt/<rama> -b <rama> origin/dev`
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
- Creación: `gh pr create --base dev --title "..." --body-file <archivo>` (`--base main` solo para releases y hotfixes, §11).
  Si `gh` no está disponible: `git push -u origin <rama>`, guarda el cuerpo en `.ai/pr-bodies/<rama>.md` y deja en `.ai/STATUS.md` el enlace
  `https://github.com/YagoGefaell/asaltamontes/compare/dev...<rama>?expand=1` para que Yago la abra.
- **Límite WIP**: como máximo **4 PRs abiertas sin revisar**. Si se alcanza, dedica la sesión a bug hunting, refinar specs de tareas futuras o atender feedback.
- La PR marca en el propio diff el `Estado` de la tarea/bugs que cierra (`hecho`/`resuelto`) para que `dev` (y después `main`) refleje la verdad tras el merge.

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
- [ ] **CI de la PR en verde en GitHub** (`gh pr checks <N> --watch`). Las ramas `main` y `dev` están protegidas: sin checks verdes la PR no se puede mergear aunque Yago la apruebe. Si la CI falla, arreglarlo en la misma rama antes de darla por lista; una PR roja no cuenta como "en revisión" sino como "en curso".

> **Nombres de los jobs de CI**: `validate-backend` y `validate-frontend` (`.github/workflows/ci.yml`) son checks obligatorios en la protección de `main`. **No se renombran ni se eliminan** sin avisar a Yago en la PR (tendría que actualizar la regla). Se pueden añadir pasos dentro de esos jobs.

## 7. Bugs

- Todo bug detectado (al leer código, al implementar o reportado por Yago) se registra en `docs/ai/bugs/BUG-xxx-<slug>.md` con la plantilla.
- Numeración: siguiente número libre mirando `dev` **y** las PRs abiertas.
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
- **Recursos (Raspberry Pi 5, D-016)**: máximo **2 agentes en paralelo**. Antes de lanzar el segundo, comprueba el margen: `free -m` (memoria disponible > 3 GB) y `uptime` (carga de 1 min < 3). Si no hay margen, trabaja en secuencial. Solo una suite completa de `./mvnw verify` a la vez; mientras otro agente la ejecuta, haz trabajo que no compile (specs, revisión, frontend). Si la app desplegada se queda sin memoria (`docker stats`), para los agentes.
- **ARM64**: cualquier imagen o binario nuevo debe tener variante `linux/arm64`.
- **Tras cada merge**: si hay acceso, verifica que el deploy terminó (`gh run list --workflow deploy.yml -L 1`) y que `/actuator/health` responde. Si falla, avisa en STATUS como bloqueo prioritario; no lo arregles tocando producción.

## 11. Rama `dev`, releases y versiones (D-019)

```
feat/Txx, fix/BUG-xxx ──PR (squash)──▶ dev ──PR de release (merge commit)──▶ main ──▶ tag vX.Y.Z + GitHub Release + deploy
hotfix/BUG-xxx (desde main) ──PR (squash)──▶ main ──▶ PR de sincronización main → dev (merge commit)
```

- **`dev`** es la rama de integración: todas las PRs de tareas, bugs y docs van aquí. Yago las revisa y mergea con **squash** (el título de la PR es el commit). `dev` **no se despliega**.
- **`main`** es producción: cada push despliega en la Pi y crea un tag.
- **PRs abiertas contra `main` antes del 2026-10-21** (cambio de flujo) siguen contra `main` hasta que se cierren; las nuevas van a `dev`.

### PR de release `dev → main` (la abre Claude con `/release`)
- **Cuándo** (decisión de Yago): cuando hay un **bloque coherente terminado** (una tarea grande o un conjunto usable, p. ej. "eventos completos" o "Fase 0"), y además:
  - CI de `dev` en verde.
  - Sin bugs **críticos** ni **altos** abiertos que afecten a lo que se publica.
  - Migraciones Flyway del bloque probadas sobre una copia con datos (o con el seed).
  - Prueba de humo del bloque en el stack `asaltamontes-dev` (§10) documentada en la PR.
- **Nunca squash**: Yago la mergea con **merge commit**. Así `main` conserva los commits `feat:`/`fix:` de cada PR, `release.yml` calcula bien la versión (`feat` → minor, `fix` → patch, `BREAKING CHANGE` → major) y `dev` y `main` no divergen.
- Título: `release: vX.Y.Z — <resumen>` (la versión es la que calculará `release.yml`; `/release` la propone).
- Una sola PR de release abierta a la vez. No cuenta para el límite WIP.

### Hotfix (algo roto en producción)
- Rama `hotfix/BUG-xxx-<slug>` desde `origin/main`, PR con `--base main` y squash. Tras el merge, Claude abre una PR `main → dev` (merge commit) para que `dev` reciba el arreglo.
- Solo para bugs críticos o producción caída; el resto va por `dev`.

### Versiones
- La versión es el **tag semver** que crea `release.yml` en `main`. La forma de mostrarla en la app y en `/actuator/info` y de inyectarla en el build es la tarea **T31**.
