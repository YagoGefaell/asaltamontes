# Registro de bugs

Cada bug tiene su archivo `BUG-xxx-<slug>.md` (plantilla en `../templates/BUG.md`). Se usa un archivo por bug para que varias PRs puedan registrar o cerrar bugs a la vez sin conflictos.

Consultas rápidas:

```bash
# Estado y severidad de todos los bugs
grep -H -m2 -E '^\- \*\*(Estado|Severidad)' docs/ai/bugs/BUG-*.md | paste - -
# Bugs abiertos críticos o altos
grep -l 'Estado:\*\* abierto' docs/ai/bugs/BUG-*.md | xargs grep -l -E 'Severidad:\*\* (crítica|alta)'
# Siguiente número libre (comprobar también las PRs abiertas)
ls docs/ai/bugs/BUG-*.md | sed -E 's/.*BUG-([0-9]+).*/\1/' | sort -n | tail -1
```

Reglas completas: `docs/ai/WORKFLOW.md` §7.
