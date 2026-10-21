---
name: end-session
description: Cierre de sesión en Asaltamontes. Deja .ai/STATUS.md al día y añade una entrada a .ai/SESSION_LOG.md para que la siguiente sesión sepa exactamente dónde seguir. Úsalo al terminar de trabajar o antes de que se agote el contexto.
---

# Cierre de sesión

```bash
ROOT=$(git rev-parse --path-format=absolute --git-common-dir)/..; AI="$ROOT/.ai"
```

1. **Nada a medias sin registrar**: cada rama con trabajo sin push → commit WIP (`wip: …`) y push, o anota en STATUS por qué no se ha subido. No dejes cambios sin commitear en worktrees sin anotarlo.
2. **Actualiza `$AI/STATUS.md`** (formato en `start-session`): en curso, PRs con enlace, siguiente en la cola, preguntas para Yago.
3. **Añade al final de `$AI/SESSION_LOG.md`** (nunca reescribas entradas anteriores):
   ```markdown
   ## AAAA-MM-DD — sesión <n> (<resumen en 5 palabras>)
   **Hecho:** PRs abiertas o actualizadas (#N Txx …), bugs registrados (BUG-xxx …).
   **Decisiones:** … (si son duraderas → también en docs/ai/DECISIONS.md vía PR docs/).
   **Aprendido / ojo con:** cosas no obvias del código o del entorno que ahorran tiempo la próxima vez.
   **Siguiente paso concreto:** la primera acción de la próxima sesión.
   ```
4. Si Yago ha dado una **preferencia general** durante la sesión ("siempre…", "no hagas…"), guárdala en la memoria de Claude y propón añadirla a `CLAUDE.md` o `WORKFLOW.md` en una PR `docs/`.
5. Limpia los worktrees de PRs ya mergeadas: `git worktree remove …` y `git branch -d …`.
6. Despídete de Yago con 3-5 líneas: qué queda en revisión y qué necesitas de él.
