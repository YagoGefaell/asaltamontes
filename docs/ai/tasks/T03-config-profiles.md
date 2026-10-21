# T03 — Configuración por perfiles, logs y Actuator

- **Estado:** hecho
- **Fase:** 0 — Cimientos
- **Depende de:** —
- **Rama sugerida:** `chore/T03-config-profiles`
- **Cierra bugs:** BUG-004, BUG-026, BUG-029
- **Tamaño:** S

## Objetivo
Una sola `application.properties` sirve hoy para todo y activa logs de SQL con parámetros (datos personales en logs). Separar entornos y dejar la app lista para operar en el servidor.

## Contexto
- `backend/src/main/resources/application.properties` (incluye `show-sql=true`, `BasicBinder=TRACE`).
- CORS ya es configurable por `CORS_ALLOWED_ORIGINS` (commit 50813e2), pero el WebSocket usa `setAllowedOriginPatterns("*")` (`WebSocketConfig.java`).
- `CookieUtil.java`: `secure(false)` hardcodeado.

## Alcance
- `application.properties` (común) + `application-dev.properties` + `application-prod.properties` + `application-test.properties` (T01).
  - Común: datasource, JWT, Google, CORS, Flyway.
  - `dev`: `show-sql=true` (sin `TRACE` de parámetros), logs DEBUG del paquete propio.
  - `prod`: sin SQL logs, `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=20s`, `server.forward-headers-strategy=framework` (detrás de nginx), compresión HTTP.
  - Perfil por `SPRING_PROFILES_ACTIVE` (compose → `prod`; `launch.json` → `dev`).
- Hikari: `maximum-pool-size` configurable (`DB_POOL_SIZE`, por defecto 10), `connection-timeout`, `leak-detection-threshold` en dev.
- `spring.jpa.open-in-view=false` (comprobar que no rompe nada: los servicios deben devolver DTOs; si algo dependía de lazy loading en la vista, arreglarlo).
- WebSocket: usar los mismos orígenes permitidos que CORS (`CorsProperties`) en `setAllowedOriginPatterns`.
- Cookie de refresh: `secure` y `sameSite` configurables (`asaltamontes.cookies.secure`, por defecto `false` mientras el servidor no tenga HTTPS (D-013); `true` cuando lo tenga, `false` en `dev`). Cambio mínimo en `CookieUtil` (D-012): mantener la clase y sus métodos; p. ej. recibir `secure` como parámetro desde `AuthController`, que lo inyecta con `@Value`.
- Actuator: `spring-boot-starter-actuator`, exponer solo `health` (con `liveness`/`readiness`) y `info` públicamente; resto deshabilitado. Permitir `/actuator/health/**` en `SecurityConfig`.
- Logs: patrón con `traceId`/request id (filtro simple que genere `X-Request-Id` y lo meta en MDC).
- Default de `CORS_ALLOWED_ORIGINS` en `dev` incluye `http://localhost:5173` (Vite).
- JVM en contenedor: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` en el Dockerfile/compose.

## Fuera de alcance
- Proxy nginx same-origin → T07 (cuando exista, CORS en prod casi no hará falta).
- Métricas/Sentry → T27.

## Criterios de aceptación
- [ ] Con perfil `prod` no se registra ningún SQL ni parámetro.
- [ ] `npm run dev` (5173) contra el backend en perfil `dev` funciona sin errores de CORS, incluido el WebSocket.
- [ ] Un origen no permitido no puede abrir el WebSocket.
- [ ] `GET /actuator/health` → 200 sin autenticación; `/actuator/env` → no expuesto.
- [ ] `.env.example`, `docker-compose.yml` y READMEs actualizados.

## Tests requeridos
- Test de integración: handshake WS desde origen no permitido → rechazado.
- Test: cookie de refresh lleva `Secure` cuando la propiedad está activa.
- Test: `/actuator/health` público.
