---
name: start-session
description: Arranque de sesión en Asaltamontes. Reconstruye el contexto (estado local, PRs abiertas, feedback de revisión, merges recientes) y decide en qué trabajar según docs/ai/WORKFLOW.md. Úsalo al principio de cada sesión o cuando haya que decidir qué hacer a continuación.
---

# Inicio de sesión

Objetivo: en pocos minutos saber **qué ha pasado desde la última sesión** y **qué toca hacer ahora**, sin preguntarle a Yago lo que ya está registrado.

## 1. Sincronizar
```bash
ROOT=$(git rev-parse --path-format=absolute --git-common-dir)/..; AI="$ROOT/.ai"
git -C "$ROOT" fetch --prune
git -C "$ROOT" status -sb
git -C "$ROOT" worktree list
```
- Si `$AI/STATUS.md` no existe, créalo a partir de la sección "Formato de STATUS.md" de abajo.
- Si el checkout principal tiene cambios sin commitear que no son tuyos, **no los toques** y anótalo.

## 2. Leer el contexto
1. `$AI/STATUS.md` completo.
2. Las **3 últimas entradas** de `$AI/SESSION_LOG.md`.
3. `docs/ai/ROADMAP.md` (orden y dependencias) y `docs/ai/DECISIONS.md` si no los conoces de esta sesión.

## 3. Reconciliar con GitHub
Con `gh` disponible (`gh auth status`):
```bash
gh pr list --state open --author "@me" --json number,title,headRefName,baseRefName,reviewDecision,isDraft,updatedAt
gh pr list --state merged --limit 15 --json number,title,headRefName,mergedAt
gh pr list --state closed --limit 10 --json number,title,headRefName,closedAt,mergedAt   # cerradas sin merge = rechazadas
```
Para cada PR abierta con actividad nueva: `gh pr view <N> --comments` y `gh api repos/{owner}/{repo}/pulls/<N>/comments` (comentarios en línea).
Sin `gh`: `git log origin/main --oneline -20` para ver los merges y pedir a Yago que pegue el feedback; anotar en STATUS que `gh` falta.

Actualiza `$AI/STATUS.md`:
- PRs mergeadas → fuera de "En revisión" y a "Hecho recientemente".
- Si hay PRs apiladas cuya padre se ha mergeado → apuntar el rebase pendiente.
- PRs con comentarios sin atender → "Feedback pendiente".
- PRs cerradas sin merge → anotar el motivo y replantear la tarea.

## 4. Decidir el trabajo (WORKFLOW.md §8)
1. Feedback pendiente → `/address-review <N>`.
2. Bugs **críticos** abiertos sin PR (`grep` en `docs/ai/bugs`, ver su README).
3. Siguiente tarea del ROADMAP con las dependencias mergeadas, o en PR si se puede apilar. Mira el `Estado` de la tarea en `main` y "En curso/En revisión" en STATUS para no duplicar trabajo.
4. Bugs **altos** sin tarea.
5. Si se ha alcanzado el **límite WIP (4 PRs sin revisar)**: bug hunting (`/bug-hunt <área>`) o refinar specs 🔍.

Si hay varias tareas independientes y la sesión lo permite, se pueden lanzar en paralelo con el agente `implementer` (`subagent_type: implementer`, `isolation: worktree`), una tarea por agente, sin superar el límite WIP. Pásale el ID de la tarea y la ruta absoluta de `.ai/`.

## 5. Informar a Yago (breve)
Un mensaje corto: qué ha cambiado desde la última sesión, qué vas a hacer ahora (IDs) y las preguntas abiertas que le bloquean (de STATUS). Después, empieza a trabajar; no esperes confirmación salvo que una pregunta abierta bloquee la tarea elegida.

## Formato de STATUS.md
```markdown
# STATUS — actualizado AAAA-MM-DD HH:MM

## En curso
- Txx — rama `…` — worktree `…` — qué falta

## En revisión (PRs abiertas)
| PR | Rama | Tarea/Bug | Base | Estado revisión | Notas |

## Feedback pendiente de atender
- #N: resumen del comentario

## Hecho recientemente (últimas 10)
- AAAA-MM-DD #N Txx — título

## Siguiente en la cola
1. …

## Bloqueos y preguntas abiertas para Yago
- [ ] …

## Notas de entorno
- (p. ej. "gh no instalado", "Docker OK")
```
