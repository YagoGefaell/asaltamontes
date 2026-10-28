---
name: ui-review
description: Revisión visual de una PR de interfaz de Asaltamontes. Saca capturas móviles (390 y 360 px, claro y oscuro) de las pantallas tocadas, las revisa contra docs/ai/DESIGN.md, corrige y publica el antes/después en la PR. Obligatorio antes de abrir o actualizar cualquier PR que cambie lo que se ve (pages, components, CSS). Úsalo con las rutas, p. ej. "/ui-review /search /events".
---

# Revisión visual (mobile first)

Argumento: rutas a capturar (`/search /events /communities/<slug>`). Sin argumento: las pantallas que toca el diff (`git diff --name-only origin/main...HEAD -- frontend/src`), traducidas a rutas con `frontend/src/app/router.jsx`.

## 0. Leer
- `docs/ai/DESIGN.md` entera (sobre todo §4, §6 y §7) y la spec de la tarea.
- Abre la pantalla existente más parecida a la que vas a tocar y úsala como modelo (DESIGN §6).

## 1. Entorno (nunca contra producción)
```bash
ROOT=$(git rev-parse --path-format=absolute --git-common-dir)/..; AI="$ROOT/.ai"
free -m   # en la Pi: hacen falta ~1,5 GB libres (backend+BD de pruebas ~1 GB, Chromium ~400 MB)
# Backend de pruebas con datos de ejemplo (WORKFLOW §10; perfil seed → usuaria demo / Montana-2026).
# Basta con db y backend: el frontend lo sirve Vite desde tu worktree.
SPRING_PROFILES_ACTIVE=prod,seed docker compose -p asaltamontes-dev -f docker-compose.yml -f docker-compose.dev.yml \
  --env-file <env de pruebas> up -d --wait db backend
cd frontend && npm ci && npx playwright install chromium   # Chromium solo la primera vez
VITE_DEV_BACKEND=http://127.0.0.1:8091 npm run dev -- --port 5173 --strictPort   # en segundo plano
```
- Si el stack `-dev` ya está levantado, reutilízalo. Si la BD de pruebas no tiene la usuaria `demo`, mira el README (sección "Demo data", `asaltamontes.seed.force`).
- Sin margen de memoria en la Pi: no hagas capturas; anótalo en la PR ("capturas pendientes") y en STATUS. No abras la PR como lista para revisar.

- Si Chromium no arranca por librerías del sistema que faltan, para y pide a Yago (STATUS → Preguntas) que ejecute una vez `sudo npx playwright install-deps chromium` en la Pi. Claude no instala paquetes del sistema.

## 2. Capturas antes y después
"Antes" sale de un worktree de `main` servido en otro puerto; "después", de tu rama:
```bash
git -C "$ROOT" worktree add --detach "$ROOT/../asaltamontes-wt/_main" origin/main 2>/dev/null || git -C "$ROOT/../asaltamontes-wt/_main" checkout -q --detach origin/main
(cd "$ROOT/../asaltamontes-wt/_main/frontend" && npm ci && VITE_DEV_BACKEND=http://127.0.0.1:8091 npm run dev -- --port 5174 --strictPort)   # en segundo plano
SHOTS_BASE_URL=http://localhost:5174 SHOTS_OUT="$AI/ui-shots/<rama>/before" npm run shots -- /search /events
SHOTS_OUT="$AI/ui-shots/<rama>/after" npm run shots -- /search /events
```
En una pantalla nueva no hay "antes": omite ese paso.
Variables: `SHOTS_BASE_URL` (por defecto `http://localhost:5173`), `SHOTS_USER`/`SHOTS_PASSWORD`, `SHOTS_FULL=1` para página completa. El script navega dentro de la SPA (cada recarga gasta un `/auth/refresh`, limitado a 30/min); si pierde la sesión, espera un minuto.

Para estados que no salen con los datos de ejemplo (vacío, error, hoja de filtros abierta, formulario con errores), haz un script de Playwright puntual en el scratchpad o captura esa pantalla tras la interacción; no lo subas al repo.

## 3. Revisar (mira cada PNG con Read, no lo des por bueno)
Para cada captura, comprueba y anota lo que falla:
- [ ] **Primer resultado visible** sin hacer scroll; controles y filtros ≤ ~20 % de la altura (DESIGN §1).
- [ ] Una sola fila de chips; el resto de filtros en hoja inferior. Sin `<select>` ni campos sueltos de filtro.
- [ ] Mismos componentes y estilo que la pantalla modelo: `Input`/`Button`, cabecera, filas, `EmptyState`, `Skeleton`.
- [ ] Una acción principal; botones sin mayúsculas; textos en femenino y sin jerga.
- [ ] Nada cortado ni desbordado a 360 px; el último elemento no queda bajo la barra inferior.
- [ ] Objetivos táctiles ≥ 44 px; contraste correcto en **oscuro** (texto pequeño legible, sin blancos sueltos).
- [ ] Color: mayoría de neutros; turquesa para acciones, naranja para contadores y fechas de quedada, amarillo casi nunca; iconos de cabecera y barra inferior en gris `--text-muted`.
- [ ] `console-errors.txt` vacío (o errores explicados).

Corrige y repite las capturas hasta que la lista salga limpia. Si algo de la guía no se puede cumplir, explícalo en la PR.

## 4. Publicar en la PR
```bash
frontend/scripts/ui-shots-publish.sh <rama> "$AI/ui-shots/<rama>/after" "$AI/ui-shots/<rama>/before" > "$AI/ui-shots/<rama>/pr-table.md"
```
Sube las capturas a la rama huérfana `ui-shots` (nunca a `main`; el repo es público, así que **solo datos de ejemplo**, nunca capturas de producción) e imprime una tabla antes/después. Pégala en la sección "Capturas" del cuerpo de la PR (`gh pr edit <N> --body-file …`) junto con la lista del §3 marcada.

## 5. Limpiar
- Para Vite. El stack `-dev` lo puedes dejar levantado si vas a seguir con UI; si no, anótalo en STATUS para que Yago lo baje (`down` está denegado).
- Las capturas locales viven en `$AI/ui-shots/` (fuera del repo); bórralas cuando la PR se mergee.
