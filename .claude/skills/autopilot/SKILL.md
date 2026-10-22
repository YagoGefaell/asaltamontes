---
name: autopilot
description: Sesión autónoma larga en el servidor de casa. Encadena start-session → trabajo (tareas, bugs, revisiones, bug hunting) → registro, PR tras PR, hasta quedarse sin trabajo seguro, bloqueada o en el límite WIP. Úsalo cuando Yago dé vía libre para trabajar sin supervisión ("/autopilot").
---

# Autopilot

Yago te da libertad para trabajar sin supervisión. Eso **no** cambia las reglas: `CLAUDE.md`, `docs/ai/WORKFLOW.md` (incluido §10, servidor compartido con producción) y `docs/ai/DECISIONS.md` (sobre todo D-012, la arquitectura de Yago) siguen siendo obligatorios.

## Bucle
0. **Antes de cada unidad de trabajo nueva**, comprueba los frenos de consumo (sección siguiente).
1. `/start-session` (sincronizar, leer STATUS y log, reconciliar con GitHub).
2. Elige el trabajo según WORKFLOW §8. En cada vuelta:
   - **Feedback de Yago** primero (`/address-review`).
   - Después tareas y bugs con `/do-task <ID>`. Si hay 2 o 3 independientes, puedes lanzar **como mucho 2 a la vez** con el agente `implementer` (`isolation: worktree`), y el segundo solo si se cumplen los umbrales de memoria y carga de WORKFLOW §10 (Raspberry Pi 5). Ante la duda, en secuencial.
   - **Recuento de PRs siempre en vivo**: Yago mergea mientras trabajas. Antes de empezar cada unidad de trabajo, y **siempre antes de decidir que el WIP está lleno o que no queda trabajo**, vuelve a consultar GitHub: `gh pr list --state open --author "@me" --json number,isDraft,title`. No uses el número que recuerdas ni el que pone STATUS. Si mientras tanto ha bajado del límite, sigue con código.
   - **Límite WIP** (4 PRs sin revisar, o el valor que Yago ponga en STATUS como `WIP: n`): si se alcanza, no abras más PRs de código. Haz, en este orden: refinar las specs 🔍 de las siguientes tareas del roadmap (PR `docs/`), `/bug-hunt` de un área no revisada recientemente, o preparar trabajo apilado **solo** si su base está en PR y es claramente correcta.
3. Tras cada PR abierta: actualiza `.ai/STATUS.md` (checkpoint) antes de seguir.
4. Cada ~3 unidades de trabajo, o si el contexto crece mucho: añade una entrada intermedia a `.ai/SESSION_LOG.md`.

## Frenos de consumo (cuota del plan de Claude de Yago)
Yago quiere conservar cuota para otros usos (2026-10-21):
- **Freno manual**: si `.ai/STATUS.md` contiene una línea `AUTOPILOT: STOP` (en cualquier sitio), no empieces nada nuevo. Termina la unidad en curso hasta un punto seguro (commit + push, PR abierta o WIP anotado), ejecuta `/end-session` y para. Yago la quita cuando quiera reanudar.
- **Freno automático**: si en la sesión aparece cualquier aviso de que el uso **semanal** está en el 80 % o más (o "approaching weekly limit"), haz lo mismo: termina la unidad en curso, `/end-session` y para. Anota en STATUS `AUTOPILOT: STOP (cuota semanal ≥ 80 %, <fecha>)`.
- No puedes leer tú el porcentaje exacto (`/usage` es un comando de Yago); no lo inventes ni lo estimes.
- Ante un aviso del límite de la ventana de horas (no el semanal), no hace falta parar: termina la unidad y continúa cuando se renueve.

## Paradas obligatorias (para y deja el motivo en STATUS → "Bloqueos")
- Una decisión que corresponde a Yago bloquea **todo** el trabajo disponible (las que bloquean solo una tarea se anotan y se pasa a otra).
- El deploy de `main` ha fallado o la app desplegada no responde → avísalo como bloqueo prioritario; no lo arregles tocando producción.
- Encuentras algo **crítico** de seguridad: regístralo, abre el hotfix si es pequeño y claro, y destácalo para Yago.
- No queda trabajo seguro: WIP lleno, specs refinadas, áreas recién revisadas. **Antes de parar por esto, repite el recuento en vivo** (ver arriba) y relee STATUS por si Yago ha respondido algo; para solo si sigue igual.
- Cualquier acción que requeriría saltarse una regla de permisos o de `settings.json`: no la intentes por otro camino.

## Nunca, aunque parezca razonable
- Merge, push a `main`, force push sin lease, borrar ramas remotas ajenas.
- Tocar contenedores, volúmenes, `.env` o datos del proyecto de producción.
- Reescribir o sustituir la autenticación u otras piezas de la arquitectura de Yago sin su OK (D-012).
- Instalar servicios en el sistema o cambiar la configuración del servidor fuera del repo.

## Al terminar
`/end-session`. El mensaje final para Yago debe ser corto y accionable: PRs listas para revisar (por orden de prioridad), bugs nuevos importantes y preguntas abiertas.
