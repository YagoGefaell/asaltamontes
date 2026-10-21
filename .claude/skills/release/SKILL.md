---
name: release
description: Prepara la PR de release dev → main de Asaltamontes (changelog, versión propuesta, checklist, prueba de humo). Úsalo cuando en dev hay un bloque coherente terminado (WORKFLOW §11) o cuando Yago lo pida.
---

# Release `dev → main`

1. **¿Procede?** (WORKFLOW §11)
   - `git fetch --tags && git log --oneline origin/main..origin/dev`: hay commits y forman un bloque coherente terminado.
   - Ninguna otra PR de release abierta: `gh pr list --base main --head dev`.
   - CI de `dev` verde (`gh run list --branch dev -L 3`).
   - Sin bugs críticos ni altos abiertos que afecten a lo publicado (`docs/ai/bugs/README.md`).
   Si algo falla, no abras la release: anótalo en STATUS.
2. **Versión propuesta**: último tag (`git describe --tags --abbrev=0 origin/main`) + bump según los commits del rango: cualquier `BREAKING CHANGE` → major; algún `feat` → minor; si no → patch.
3. **Prueba de humo** del contenido de `dev` en el stack `asaltamontes-dev` (WORKFLOW §10): login, registro, chat en tiempo real y los flujos del bloque. Si hay migraciones nuevas, que se apliquen sobre una BD con datos (seed). Al terminar, baja el stack.
4. **Cuerpo de la PR** (`.ai/pr-bodies/release-vX.Y.Z.md`):
   - Resumen en lenguaje de producto (qué notarán las usuarias).
   - Changelog agrupado (Funcionalidades / Correcciones / Infra / Docs) con enlaces a cada PR.
   - Migraciones de BD incluidas y si son reversibles.
   - Cambios de configuración o de `.env` del servidor que Yago deba hacer **antes** de mergear.
   - Riesgos y plan de vuelta atrás (volver a desplegar el tag anterior).
   - Recordatorio: **mergear con "Create a merge commit"**, nunca squash.
5. `gh pr create --base main --head dev --title "release: vX.Y.Z — <resumen>" --body-file …`
6. Espera la CI de la PR. STATUS → "Release abierta". Tras el merge: comprueba que `release.yml` creó el tag esperado y que el deploy terminó bien y está `healthy`.
