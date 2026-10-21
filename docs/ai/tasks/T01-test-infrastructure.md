# T01 — Infraestructura de tests y CI que los ejecuta

- **Estado:** pendiente
- **Fase:** 0 — Cimientos
- **Depende de:** —
- **Rama sugerida:** `test/T01-test-infrastructure`
- **Cierra bugs:** BUG-037
- **Tamaño:** M

## Objetivo
Hoy no hay ningún test real y la CI solo compila. Cada tarea posterior debe poder demostrar con tests que funciona y que no rompe nada. Esta tarea va primero.

## Contexto
- `backend/src/test/.../AsaltamontesApplicationTests.java`: solo `contextLoads()`, necesita una BD real y variables de entorno → no se ejecuta en CI.
- `.github/workflows/ci.yml`: backend `./mvnw clean compile`, frontend `lint` + `build`. Se dispara en `pull_request` a `main`.
- Frontend sin runner de tests.

## Alcance
### Backend
- Añadir dependencias de test: `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`, `spring-boot-testcontainers` (versiones gestionadas por el BOM de Spring Boot).
- `src/test/resources/application-test.properties` con valores fijos para JWT (secreto de test ≥ 32 chars), Google (dummy), CORS.
- Clase base `AbstractIntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, contenedor `PostgreSQLContainer` estático con `@ServiceConnection`, imagen `postgres:17-alpine` para alinearse con T08).
- Utilidades de test: `TestDataFactory` (crear usuaria + perfil, comunidad, conversación) y helper para obtener un access token (`AuthTestHelper.loginAs(username)` usando `JwtUtil`).
- Reescribir `AsaltamontesApplicationTests` para que extienda la base.
- **Tests de humo** que fijen el comportamiento actual (no arreglar bugs aquí):
  - `AuthControllerIT`: registro OK devuelve 201 + `accessToken` + cookie `refreshToken`; login correcto/incorrecto.
  - `UserControllerIT`: `GET /api/users/me` con token → 200; sin token → 401/403 (documentar el valor actual, T04 lo cambiará).
  - `CommunityControllerIT`: crear comunidad y listarla.
- `@Scheduled` no debe dispararse en tests (propiedad para desactivar scheduling en perfil `test`, p. ej. `@EnableScheduling` en una `@Configuration` con `@Profile("!test")`).

### Frontend
- Añadir Vitest + `@testing-library/react` + `@testing-library/jest-dom` + `jsdom`.
- Script `"test": "vitest run"` y `"test:watch": "vitest"`.
- Configuración en `vite.config.js` (`test: { environment: 'jsdom', setupFiles: ... }`).
- Tests iniciales: `src/lib/api-client.test.js` (respuesta JSON OK, error ProblemDetail → `error.message`/`error.errors`, reintento tras refresh con `fetch` mockeado) y `src/utils/validation.test.js`.

### CI
- `ci.yml` backend: `./mvnw -B verify` (los runners `ubuntu-latest` tienen Docker → Testcontainers funciona). Caché de Maven (`actions/setup-java` con `cache: maven`).
- `ci.yml` frontend: `npm ci` (no `npm install`), `npm run lint`, `npm test`, `npm run build`. Caché npm.
- Publicar informe de tests como artefacto si falla (`surefire-reports`, `failsafe-reports`).

## Fuera de alcance
- Arreglar bugs detectados al escribir los tests → registrar y seguir.
- E2E con navegador (Playwright) → se valorará en T28.

## Criterios de aceptación
- [ ] `cd backend && ./mvnw verify` pasa en local con Docker y sin `.env`.
- [ ] `cd frontend && npm test` pasa.
- [ ] La CI de la PR ejecuta y muestra ambos jobs en verde.
- [ ] README del backend y del frontend explican cómo ejecutar los tests.

## Tests requeridos
Los descritos arriba (son el entregable).

## Notas y riesgos
- Testcontainers necesita Docker en local; documentarlo.
- Si el job de deploy (`deploy.yml`, self-hosted) compila con `-DskipTests` en el Dockerfile, sigue así; los tests se ejecutan en CI, no en el build de imagen.
