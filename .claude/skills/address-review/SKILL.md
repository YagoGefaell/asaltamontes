---
name: address-review
description: Atiende el feedback de Yago en una PR abierta de Asaltamontes. Lee los comentarios (generales y en línea), aplica los cambios en la misma rama o justifica por qué no, y responde a cada comentario. Úsalo con el número de PR, p. ej. "/address-review 12".
---

# Atender una revisión

1. Contexto:
   ```bash
   gh pr view <N> --json title,headRefName,baseRefName,reviewDecision,body
   gh pr view <N> --comments
   gh api repos/{owner}/{repo}/pulls/<N>/comments --jq '.[] | {id, path, line, body, in_reply_to_id}'
   ```
   Sin `gh`, pide a Yago que pegue el feedback.
2. Ve al worktree de la rama (o créalo: `git worktree add ../asaltamontes-wt/<rama> <rama>`) y haz `git pull`.
3. Para **cada** comentario, decide:
   - **Aplicar**: cambio + test si corresponde, y un commit por comentario o tema (`fix(chat): address review — validate group size`).
   - **No aplicar**: solo con un motivo técnico concreto; respóndelo en el hilo.
   - **Duda**: pregunta en el hilo y deja el resto avanzado.
4. Si la base ha cambiado (la PR padre se ha mergeado o su base, `dev` o `main` en releases y hotfixes, ha avanzado con conflictos): `git rebase origin/<base>` y `git push --force-with-lease`. Es el único caso en que se reescribe historia.
5. Vuelve a pasar la verificación completa (WORKFLOW §6).
6. Responde en GitHub: `gh pr comment <N> --body "…"` con un resumen (qué se ha aplicado, qué no y por qué) y responde en los hilos en línea: `gh api repos/{owner}/{repo}/pulls/<N>/comments/<id>/replies -f body="…"`.
7. **Preferencias generales** de Yago ("en general prefiero…") → memoria de Claude + propuesta de PR `docs/` sobre `CLAUDE.md`, `WORKFLOW.md` o `DECISIONS.md`.
8. Actualiza `$AI/STATUS.md` (feedback atendido, esperando una nueva revisión).
