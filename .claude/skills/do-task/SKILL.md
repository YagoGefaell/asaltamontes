---
name: do-task
description: Ejecuta de principio a fin una tarea del roadmap (Txx) o un bug (BUG-xxx) de Asaltamontes siguiendo el flujo de ramas y PRs. Úsalo con el ID como argumento, p. ej. "/do-task T09" o "/do-task BUG-006".
---

# Ejecutar una tarea o un bug

Argumento: `Txx`, `Txxa` (sub-PR) o `BUG-xxx`. Sin argumento: elige con `/start-session` §4.

## 0. Preparar
```bash
ROOT=$(git rev-parse --path-format=absolute --git-common-dir)/..; AI="$ROOT/.ai"
git -C "$ROOT" fetch --prune
```
- Lee la spec: `docs/ai/tasks/<ID>-*.md` o `docs/ai/bugs/<ID>-*.md`, y además `CLAUDE.md`, `docs/ai/WORKFLOW.md` y las decisiones aplicables de `docs/ai/DECISIONS.md`. Si la tarea toca lo que se ve (pantallas, componentes, CSS), también `docs/ai/DESIGN.md`.
- Comprueba que las **dependencias** están mergeadas en `origin/main`. Si alguna está en PR abierta, apila (WORKFLOW §2). Si no existe, para y elige otra tarea.
- Comprueba en STATUS y en las PRs abiertas que nadie (otro agente) la tiene ya en curso.
- Rama y worktree (si no estás ya en un worktree aislado):
  `git -C "$ROOT" worktree add "$ROOT/../asaltamontes-wt/<rama>" -b <rama> origin/main` → trabaja en esa carpeta.
- Anota en `$AI/STATUS.md` → "En curso".

## 1. Refinar (solo specs 🔍 o si la spec ha quedado desfasada)
Rellena la plantilla `docs/ai/templates/TASK.md` con lo que encuentres en el código **actual**: archivos concretos, alcance, criterios y tests. Si el alcance cambia de forma relevante respecto a lo planificado, o la tarea es `L`, divídela en sub-PRs (`T17a`, `T17b`…) y documéntalo en la spec. La spec refinada va en el primer commit de la PR.

## 2. Entender antes de tocar
- Lee los archivos de "Contexto" y sus tests.
- En bugs con confianza "probable": **escribe primero el test que lo reproduce**. Si no falla, el bug se cierra como `descartado (motivo)` en una PR `docs/` y se termina.

## 3. Implementar
- Commits pequeños en conventional commits (WORKFLOW §3), con la línea de co-autoría.
- Imita el estilo del código vecino. No hagas refactors fuera de alcance.
- ¿Encuentras otro bug? Regístralo (nuevo archivo `BUG-xxx`, siguiente número libre) **en esta misma PR solo como documento**, sin arreglarlo, y menciónalo en la descripción.
- Cambios de esquema → nueva migración Flyway.

## 4. Verificar (Definition of Done, WORKFLOW §6)
```bash
cd backend && ./mvnw -B verify
cd frontend && npm ci && npm run lint && npm test && npm run build
```
- Pruebas manuales que tengan sentido (p. ej. levantar el stack y probar el flujo); anota los pasos para la sección "Cómo probarlo".
- Si cambia lo que se ve: **`/ui-review`** con las rutas tocadas (capturas antes/después en la PR). Sin capturas no está terminada.
- Autorrevisión del diff (`git diff origin/main...HEAD`) y, si está disponible, `/code-review`.
- Actualiza en el diff el `Estado` de la tarea a `hecho` y el de los bugs que cierra a `resuelto`, y también `docs/api-contract-v1.json`, `.env.example` y los READMEs si aplica.

## 5. PR
- Push: `git push -u origin <rama>`.
- Cuerpo según `docs/ai/templates/PR_BODY.md` → guárdalo en `$AI/pr-bodies/<rama>.md`.
- `gh pr create --base <main|rama-padre> --title "<conventional title> (<ID>)" --body-file "$AI/pr-bodies/<rama>.md"`.
  Sin `gh`: deja en STATUS el enlace `https://github.com/YagoGefaell/asaltamontes/compare/main...<rama>?expand=1`.
- Espera la CI: `gh pr checks <N> --watch`. Si falla, arréglalo en la rama; la PR no pasa a "En revisión" hasta tener los checks verdes.
- STATUS: mueve la tarea de "En curso" a "En revisión" con el número o enlace de la PR.

## 6. No esperar
Con la PR abierta, la tarea queda en manos de Yago. Vuelve a `/start-session` §4 para elegir lo siguiente (respetando el límite WIP).
