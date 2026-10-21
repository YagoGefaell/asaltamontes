# T31 — Versión visible y releases fiables

- **Estado:** pendiente
- **Fase:** Transversal (CI/CD)
- **Depende de:** —
- **Rama sugerida:** `ci/T31-versioning`
- **Cierra bugs:** —
- **Tamaño:** M

## Objetivo
Con el flujo `dev → main` (D-019), cada release crea un tag semver, pero la versión no se ve en ningún sitio: el frontend tiene `APP_CONFIG.VERSION = '1.0.0'` fijo y el backend `0.0.1-SNAPSHOT` en el `pom.xml`. Yago y las testers deben saber qué versión están usando, y los informes de error (T27) deben llevarla.

## Contexto
- `release.yml` (push a `main`): `mathieudutour/github-tag-action` crea el tag a partir de los conventional commits y `ncipollo/release-action` publica la Release.
- `deploy.yml` (push a `main`, runner self-hosted) se ejecuta **en paralelo** con `release.yml`, así que hoy no puede saber el tag nuevo (carrera).

## Alcance
- **Orden release → deploy**: que el deploy se ejecute después de crear el tag. Opciones: `deploy.yml` con `on: workflow_run` de "Release & Versioning" (completed + success) o un único workflow con dos jobs (`needs:`). Mantener `paths-ignore`, la concurrencia y el smoke test. Comprobar que los checks obligatorios de la protección no cambian.
- **Inyectar la versión en el build**: `APP_VERSION` (tag, p. ej. `v1.4.0`) + `GIT_SHA` corto como build args.
  - Frontend: `VITE_APP_VERSION` → `APP_CONFIG.VERSION`; mostrarla en Ajustes (pie: "Asaltamontes v1.4.0 · abc1234").
  - Backend: `info.app.version`/`info.git.sha` en `/actuator/info` (o `build-info` de Spring Boot) y en el log al arrancar.
  - Sin tag (build local o dev): `0.0.0-dev+<sha>`.
- **Releases en GitHub**: revisar que el changelog agrupa por tipo y enlaza las PRs. Con merge commits, la acción ve los commits de `dev`; verificar que no duplica entradas.
- **Docs**: README (cómo se versiona y dónde ver la versión) y WORKFLOW §11.

## Criterios de aceptación
- [ ] Tras mergear una release, el tag nuevo aparece en `/actuator/info` y en Ajustes sin intervención manual.
- [ ] El deploy de `main` no empieza antes de que exista el tag (verificado en el historial de Actions).
- [ ] Un push de solo docs a `main` no despliega (igual que ahora).

## Tests requeridos
- Test del frontend: el pie de Ajustes muestra la versión inyectada.
- IT del backend: `/actuator/info` incluye la versión.
