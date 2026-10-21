# T27 — Observabilidad: Sentry, métricas y uptime

- **Estado:** pendiente
- **Fase:** 4 — Demo privada
- **Depende de:** T03
- **Rama sugerida:** `chore/T27-observability`
- **Cierra bugs:** —
- **Tamaño:** M
- **Spec:** 🔍 alto nivel — **refinar antes de implementar** (completar con la plantilla `docs/ai/templates/TASK.md` mirando el código de ese momento).

> Raspberry Pi 5 (D-016): cualquier imagen nueva (MinIO, Prometheus, Grafana, Uptime Kuma…) debe existir para `linux/arm64`, y hay que vigilar la RAM; si no cabe, preferir servicios externos gratuitos.

## Objetivo
Durante la demo hay que enterarse de los errores antes de que las testers los cuenten.

## Enfoque
- Sentry (plan gratuito) en el backend (`sentry-spring-boot-starter-jakarta`) y el frontend (`@sentry/react`), con DSN por entorno, `release` = versión del tag y sin datos personales (`sendDefaultPii=false`, filtrar emails y mensajes).
- Actuator + Micrometer Prometheus (`/actuator/prometheus`, accesible solo desde la red interna) y, opcionalmente, Grafana y Prometheus en el compose con un panel básico: RPS, latencia p95, errores 5xx, pool de Hikari, sesiones WS activas y JVM.
- Uptime: Uptime Kuma en el compose o un servicio externo gratuito que vigile `/actuator/health`.
- Logs en JSON en `prod` (logback encoder) con rotación (opciones de logging de Docker).
