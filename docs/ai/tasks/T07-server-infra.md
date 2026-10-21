# T07 — Infra del servidor privado: proxy, compose, deploy y backups

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** —
- **Rama sugerida:** `chore/T07-server-infra`
- **Cierra bugs:** BUG-027, BUG-028 (parte)
- **Tamaño:** M

## Objetivo
Que la app funcione de forma estable en el servidor privado: un único origen (sin CORS ni problemas de cookies), BD no expuesta, healthchecks, despliegues sin caída larga y backups automáticos.

## Contexto
- `docker-compose.yml`: `db` (postgres:13, puerto `5433:5432` abierto), `backend` (`8080:8080`), `frontend` nginx (`80:80`). `depends_on` sin condiciones, sin healthchecks ni `restart`.
- `frontend/nginx.conf`: `location /api/ { proxy_pass http://backend:8080/; }` → elimina el prefijo `/api` (roto) y no hay proxy para `/auth` ni `/ws-asaltamontes`.
- El frontend llama al backend por `VITE_API_URL` (puerto 8080 directo).
- `.github/workflows/deploy.yml` (runner self-hosted): `docker compose down` + `up --build -d` → caída durante todo el build. Se dispara también con cambios solo de docs.

## Alcance
### nginx (frontend/nginx.conf)
- `location /api/`, `location /auth/` → `proxy_pass http://backend:8080;` (**sin** barra final, conserva la ruta).
- `location /ws-asaltamontes` → proxy con `Upgrade`/`Connection` y `proxy_read_timeout 1h`.
- `location /actuator/health` → backend (para monitorización externa), resto de `/actuator` denegado.
- Cabeceras `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Real-IP`.
- Caché larga (`immutable`, 1 año) para `/assets/*` con hash; `no-cache` para `index.html`.
- gzip; cabeceras de seguridad (`X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`/CSP básica).
- `client_max_body_size` (preparado para T17, p. ej. 10m).

### Frontend
- `VITE_API_URL` vacío por defecto en build de producción → peticiones relativas al mismo origen. `useWebSocket` construye la URL `ws(s)://` desde `window.location` si no hay `VITE_API_URL`.
- En desarrollo (`npm run dev`), proxy de Vite (`server.proxy`) para `/api`, `/auth` y `/ws-asaltamontes` → `localhost:8080`, para que dev y prod se comporten igual.

### docker-compose.yml
- `db`: sin `ports` públicos (o `127.0.0.1:5433:5432` si Yago necesita acceso local con DBeaver — documentarlo), `healthcheck` con `pg_isready`, `restart: unless-stopped`.
- `backend`: sin `ports` públicos (solo accesible vía nginx), `healthcheck` sobre `/actuator/health` (requiere T03; si T03 no está mergeada, usar `curl` al puerto o dejar el healthcheck para T03), `depends_on: db: condition: service_healthy`, `restart`, `SPRING_PROFILES_ACTIVE=prod`, límites de memoria.
- `frontend`: `depends_on: backend: condition: service_healthy`, `restart`.
- Quitar `container_name` fijos si estorban (o mantener por compatibilidad con los scripts de Yago — preguntar).
- Servicio `backup`: imagen postgres con cron (o `prodrigestivill/postgres-backup-local`) que hace `pg_dump` diario comprimido a un volumen/carpeta del host con retención 7 diarios + 4 semanales. Documentar **cómo restaurar** y probarlo.

### deploy.yml
- `paths-ignore: ['docs/**', '**/*.md']`.
- `docker compose build` primero (con la app antigua aún corriendo) y luego `docker compose up -d --remove-orphans` (recrea solo lo que cambió). Sin `down`.
- `docker image prune -f` al final.
- Paso de verificación: esperar a que `backend` esté `healthy` (timeout 3 min) y fallar el job si no.

## Fuera de alcance
- HTTPS con **Tailscale** (D-013): primero una prueba de conectividad documentada (instalar Tailscale en la Pi y en un móvil fuera de la red de casa → `tailscale status`/`tailscale ping`). Si funciona, nueva tarea `T07b` con `tailscale serve` + certificado y cookies `Secure`. La instalación de Tailscale en el sistema la hace Yago (Claude no instala servicios del sistema). Si no funciona, plan B con mkcert.
- Upgrade de Postgres → T08.

## Criterios de aceptación
- [ ] Desde el navegador, todo el tráfico va al puerto 80 (o al configurado); el 8080 y el 5433 no son accesibles desde otra máquina de la red.
- [ ] Login, refresh (cookie) y chat por WebSocket funcionan a través de nginx.
- [ ] Un despliegue no deja la app caída más de unos segundos.
- [ ] Existe al menos un backup generado y se ha probado su restauración en una BD temporal (documentado en la PR).
- [ ] Una PR que solo toca `docs/` no dispara el deploy.

## Tests requeridos
- CI: `docker compose config` valida el compose.
- Manual en el servidor (Yago o Claude si tiene acceso): checklist de la PR.

## Notas y riesgos
- **Raspberry Pi 5 / ARM64** (D-016): todas las imágenes deben ser multi-arch con `linux/arm64` (`docker manifest inspect <imagen>`). Límites de memoria por servicio (orientativo para 8 GB: backend 768 MB con `-XX:MaxRAMPercentage=75`, Postgres 512 MB con `shared_buffers=128MB`, nginx 64 MB) y ajustarlos a la RAM real de la Pi.
- **Claude trabaja en esta misma máquina** (ver `WORKFLOW.md` §10). Los cambios de compose se prueban con `docker compose -p asaltamontes-dev` y puertos alternativos, nunca recreando el proyecto de producción a mano. El deploy real lo hace el workflow al mergear.
- Requiere probar en el servidor real. La PR debe incluir el checklist de verificación post-merge.
- **Hallazgos del 2026-10-21** (ver BUG-028):
  - La Pi tiene **4 GB** de RAM (no 8): ajustar los límites de memoria a la baja (orientativo: backend 640 MB, Postgres 384 MB, nginx 64 MB) y dejar margen para el build del deploy y para los agentes.
  - `restart: unless-stopped` en todos los servicios es imprescindible: sin él la app estuvo meses caída tras un reinicio.
  - El runner self-hosted puede desaparecer (GitHub borra los runners inactivos). Proponer una alerta sencilla: p. ej. un workflow programado en `ubuntu-latest` que avise (issue o fallo visible) si el último deploy lleva más de N horas en cola, o un healthcheck externo de la app.
  - `/etc/github-runner.env` debe tener permisos `600` y dueño el usuario del runner (estaba legible por todos; corregido a mano el 2026-10-21). Documentarlo en el README de despliegue junto con la **lista completa de variables** que debe tener (incluidas `CORS_ALLOWED_ORIGINS` y las de T03).
  - La IP de la Pi cambió de .157 a .158 y el frontend desplegado dejó de funcionar hasta que se corrigieron a mano `VITE_API_URL` y `CORS_ALLOWED_ORIGINS` en `/etc/github-runner.env` (2026-10-21). Con el proxy same-origin y `VITE_API_URL` vacío, el frontend deja de depender de la IP y CORS deja de ser necesario en producción.
  - Añadir al paso de verificación del deploy un preflight CORS (o una petición real a `/auth/refresh` a través de nginx) para que una configuración rota haga fallar el job en vez de salir verde.
